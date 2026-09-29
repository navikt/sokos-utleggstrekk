# Periodeberegning

Denne siden forklarer hvordan sokos-utleggstrekk beregner hvilke perioder som skal sendes til Oppdrag Z når et nytt trekkpålegg mottas fra Skatteetaten. Dette er den mest komplekse delen av forretningslogikken.

## Oversikt

Oppdrag Z opererer med **endringer** (diff), ikke med øyeblikksbilder. Skatteetaten derimot sender alltid et komplett bilde av gjeldende perioder. sokos-utleggstrekk må derfor:

1. Vite hva som allerede er sendt til OS
2. Sammenligne med det nye trekkpålegget
3. Beregne forskjellen (nye perioder, fjernede perioder)
4. Sende kun endringene

---

## Steg-for-steg algoritme

| Steg | Handling | Detaljer |
|------|----------|----------|
| 1 | Hent perioder fra trekkpålegget | Fra tabellen `periode` |
| 2 | Juster datoer til hele måneder | `mapNewFomTom()` |
| 3 | Finn alle relevante trekkalternativ | Union av nye + tidligere sendte |
| 4 | Hent kjente perioder fra OS | Filtrert: gyldige, med sats |
| 5 | Finn OS-perioder som ikke finnes i trekkpålegget | Disse må **nulles** |
| 6 | Finn trekkperioder som ikke finnes i OS | Disse er **nye** |
| 7 | Kombiner nullinger + nye perioder | Slå sammen steg 5 og 6 |
| 8 | Returner perioder per trekkalternativ | Ferdig resultat |

---

## Steg 1–2: Hent og juster perioder

Periodene hentes fra databasetabellen `periode` (tilhørende trekkpålegget) og justeres til hele måneder:

| Opprinnelig dato | Justert dato | Regel |
|------------------|-------------|-------|
| startdato → | 1. i samme måned | Alltid rund ned |
| sluttdato → | Siste dag i samme måned | Alltid rund opp |
| sluttdato = null | Forblir null | Åpen periode |

### Håndtering av overlapp etter avrunding

Perioder sorteres fra nyeste til eldste. Når en nyere periode overlapper med en eldre etter avrunding, **kuttes den eldre periodens sluttdato** til dagen før den nyere periodens startdato:

| | Periode A | Periode B |
|---|---|---|
| **Før justering** | 15.jan – 6.mar, 5% | 7.mar – åpen, 10% |
| **Etter justering** | 1.jan – 28.feb, 5% | 1.mar – åpen, 10% |

Forklaring: Periode A ville fått sluttdato 31. mars (siste dag i mars), men periode B starter 1. mars. Fordi B er nyere, overskriver den A – og A kuttes til 28. februar.

**Kode**: `mapNewFomTom()` i `TrekkFraSkatt.kt`

---

## Steg 3: Finn relevante trekkalternativ

"Kjente alternativ" er settet av trekkalternativ (LOPP og/eller LOPM) som er relevante for dette trekkpålegget. Settet bygges fra **to kilder**:

| Kilde | Innhold |
|---|---|
| **Kilde 1: Perioder i trekkpålegget** | Periode med trekkprosent → LOPP |
| | Periode med trekkbeløp → LOPM |
| **Kilde 2: Tidligere sendt til OS** | `SELECT DISTINCT trekk_alternativ FROM transaksjon_os WHERE trekk_id_ske = trekkid AND kvittering_status NOT IN (FEIL, UKJENT) AND transaksjon_status = SENDT` |
| **Resultat** | Union av begge sett → Relevante alternativ, f.eks. {LOPP, LOPM} |

### Hvorfor begge kilder?

Eksempel: Et trekkpålegg hadde i versjon 1 både prosent- og beløpperioder. Vi sendte LOPP og LOPM til OS. I versjon 2 har alle perioder blitt prosent. Vi trenger likevel å vite at LOPM finnes i OS – for å **nulle** de gamle beløpperiodene.

Uten kilde 2 ville vi bare sett LOPP i det nye trekkpålegget og glemt å oppdatere LOPM i OS.

**Kode**: `getOsAlternativForTrekk()` i `Repository.kt`

---

## Steg 4: Hent kjente perioder fra OS

For hvert trekkalternativ hentes perioder som allerede er sendt til OS:

```sql
SELECT * FROM periode_til_os p
JOIN transaksjon_os t ON p.transaksjon_os_id = t.id
WHERE trekk_id_ske = :trekkid
  AND t.trekk_alternativ = :alternativ
  AND t.transaksjon_status = 'SENDT'
  AND t.kvittering_status IN ('IKKE_MOTTATT', 'OK')
ORDER BY p.id ASC
```

Deretter filtreres "foreldede" perioder bort. En periode anses foreldet hvis:
- Satsen er 0 (allerede nullet)
- Sluttdatoen har passert (utløpt)
- Det finnes en nyere periode med samme fom/tom og sats=0 (eksplisitt nulling)

**Kode**: `obsoleted()` i `BehandleTrekkService.kt`

---

## Steg 5: Finn perioder som må nulles

Sammenligner kjente OS-perioder med trekkpåleggets perioder. OS-perioder som **ikke lenger finnes** i trekkpålegget (basert på fom+tom+sats) er fjernet av Skatteetaten og må nulles i Oppdrag Z:

| Periode | Sats | I OS? | I SKE? | Resultat |
|---|---|---|---|---|
| jan–mar | 5% | ✓ | ✓ | Ignorer (uendret) |
| apr–jun | 8% | ✓ | ✗ | **Null** (sats=0) |
| jul–åpen | 10% | ✓ | ✓ | Ignorer (uendret) |

For perioden "apr–jun 8%" lages det en ny periode: `apr–jun, sats=0.0` som sendes til OS for å deaktivere den.

---

## Steg 6: Finn nye perioder

Perioder i trekkpålegget som **ikke finnes** blant de kjente OS-periodene er nye og skal sendes:

| Periode | Sats | I SKE? | I OS? | Resultat |
|---|---|---|---|---|
| jan–mar | 5% | ✓ | ✓ | Allerede kjent, skip |
| apr–jun | 12% | ✓ | ✗ | **NY** periode, send til OS |
| jul–åpen | 10% | ✓ | ✓ | Allerede kjent, skip |

### Viktig: Nye perioder lages for ALLE relevante alternativ

Selv om en ny periode fra SKE er en prosentperiode, lages det en periode for **begge** alternativ (LOPP og LOPM) hvis begge er i det relevante settet. Prosentalternativet får den ekte verdien, mens beløpalternativet får sats=0:

```
Ny SKE-periode: apr–jun, 12% (prosent)
Relevante alternativ: {LOPP, LOPM}

→ Ny LOPP-periode: apr–jun, sats=12.0
→ Ny LOPM-periode: apr–jun, sats=0.0
```

---

## Steg 7–8: Kombiner og returner

De to listene (nullinger fra steg 5 + nye fra steg 6) slås sammen per trekkalternativ og returneres som `PerioderTilOS`.

---

## Komplett eksempel

### Utgangspunkt i OS

Tidligere sendt (trekkid=X, LOPP):
- jan–mar, 5%
- apr–jun, 8%

### Nytt trekkpålegg (versjon 2)

Perioder fra SKE:
- jan–mar, 5% (prosent)
- apr–jun, 500 kr (beløp – ny type!)
- jul–åpen, 10% (prosent)

### Beregning

**Steg 3**: Relevante alternativ = {LOPP (fra perioder + OS), LOPM (fra perioder)}

**Steg 4**: Kjente OS-perioder for LOPP: [jan–mar 5%, apr–jun 8%]. For LOPM: [] (tomt – aldri sendt)

**Steg 5** (nullinger):
- LOPP: apr–jun 8% finnes i OS men ikke i SKE (SKE har beløp der, ikke prosent) → null: apr–jun, sats=0.0
- LOPM: ingenting å nulle (tomt i OS)

**Steg 6** (nye perioder):
- SKE-periode jan–mar 5% finnes allerede i OS LOPP → skip
- SKE-periode apr–jun 500kr → ikke i OS:
  - LOPP: apr–jun, sats=0.0
  - LOPM: apr–jun, sats=500.0
- SKE-periode jul–åpen 10% → ikke i OS:
  - LOPP: jul–åpen, sats=10.0
  - LOPM: jul–åpen, sats=0.0

**Steg 7** (kombiner):

| Alternativ | Perioder til OS |
|------------|----------------|
| LOPP | apr–jun sats=0 (nulling), apr–jun sats=0 (ny), jul–åpen sats=10 (ny) |
| LOPM | apr–jun sats=500 (ny), jul–åpen sats=0 (ny) |

*Merk*: LOPP har to perioder for apr–jun med sats=0 – én fra nullingen (steg 5) og én fra den nye beløpperioden (steg 6). Oppdrag Z håndterer dette korrekt.

---

## Sammenligning: sameAs()

To perioder anses som "like" hvis de har:
- Samme `startdato`/`periodeFomDato`
- Samme `sluttdato`/`periodeTomDato`
- Samme sats (for det aktuelle alternativet)

Hvis noen av disse er forskjellig, er perioden "ny" og må sendes.

**Kode**: `PeriodeFraSkatt.sameAs(PeriodeTilOS)` i `TrekkFraSkatt.kt`
