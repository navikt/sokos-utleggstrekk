# Trekk-transformasjon – sokos-utleggstrekk

Dette dokumentet beskriver forretningslogikken i `BehandleTrekkService` – den mest komplekse delen av applikasjonen.

## Problemet

Skatteetaten og Oppdrag Z har **ulike datamodeller** for trekk:

|                    | Skatteetaten                          | Oppdrag Z                    |
|--------------------|---------------------------------------|------------------------------|
| **Perioder**       | Kombinerte prosent- og beløpsperioder | Separate dokumenter per type |
| **Periodegrenser** | Vilkårlige datoer                     | Første/siste dag i måneden   |
| **Versjonering**   | Sekvensnummer + trekkversjon          | Aksjonskode (NY/ENDR/OPPH)   |

`BehandleTrekkService` løser disse forskjellene.

---

## Splitting av trekk-alternativ (P og M)

Et trekkpålegg fra Skatteetaten kan inneholde **begge** typer satser innenfor samme eller ulike perioder:

```
SKE-trekk:
  Periode 1: Jan–Mar, trekkprosent=15%
  Periode 2: Apr–Jun, trekkbeloep=2000kr
  Periode 3: Jul–  , trekkprosent=10%, trekkbeloep=1500kr
```

Dette splittes i **to** OS-dokumenter:

```
Prosenttrekk (P / LOPP):
  Periode 1: 01.01–31.03, sats=15%
  Periode 2: 01.04–30.06, sats=0%  ← nulles ut (perioden fantes ikke)
  Periode 3: 01.07–    , sats=10%

Beløpstrekk (M / LOPM):
  Periode 1: 01.01–31.03, sats=0   ← nulles ut
  Periode 2: 01.04–30.06, sats=2000
  Periode 3: 01.07–    , sats=1500
```

### Regler for splitting

1. Hvis trekkpålegget bare har prosentperioder → kun P-dokument lages
2. Hvis trekkpålegget bare har beløpsperioder → kun M-dokument lages
3. Hvis trekkpålegget har begge typer → **to dokumenter** lages
4. Perioder uten en gitt type nulles ut (sats=0) i det tilhørende dokumentet

---

## Periodejustering til månedgrenser

Oppdrag Z krever at perioders start- og sluttdato er justert til **første/siste dag i måneden**.

```
SKE-periode:  07.03.2024 – 14.08.2024
OS-periode:   01.03.2024 – 31.08.2024
```

### Håndtering av overlappende perioder

Når to SKE-perioder havner innenfor samme kalendermåned etter justering, lar vi **påfølgende periode overstyre** den forrige:

```
SKE:
  Periode A: slutter 06.03 → justert til 31.03
  Periode B: starter 07.03 → justert til 01.03

Løsning:
  Periode A justeres til å slutte 28.02 (siste dag i forrige måned)
  Periode B starter 01.03 som planlagt
```

Logikken er implementert i funksjonen `mapNewFomTom()` i databasemodellen.

---

## Bestemmelse av aksjonskode

`BehandleTrekkService` bestemmer aksjonskode ved å slå opp trekket i `transaksjon_os`-tabellen og se på periodene:

```
1. Slå opp trekk_id_ske i transaksjon_os-tabellen
   ├── Finnes ikke → aksjonskode = NY
   └── Finnes → sammenlign perioder mot det som er i OS
       ├── trekkstatus = AVSLUTTET → aksjonskode = OPPH (ingen perioder sendes)
       ├── Ingen periodemessige endringer → send ikke noe
       └── Perioder er endret → aksjonskode = ENDR
           ├── Perioder som er fjernet → legges til med sats=0 (nullering)
           └── Perioder som er nye eller endret → legges til med ny sats
```

### Nullering av fjernede perioder

Hvis en periode eksisterer i OS, men ikke lenger i det nye SKE-trekket, **nulles den ut** i ENDR-dokumentet:

```
Eksisterende i OS: Periode Jan–Mar, sats=15%
Nytt SKE-trekk: Periode Apr– , sats=10%

Resultat-ENDR:
  Periode Jan–Mar, sats=0%   ← nullering av gammel periode
  Periode Apr–  , sats=10%   ← ny periode
```

### Uendelig periode som endres til endelig

En periode uten sluttdato som nå får en sluttdato behandles som **to** oppdateringer:

```
Gammel OS-periode: Jan–    (uendelig), sats=15%
Ny SKE-periode:    Jan–Mar (endelig),  sats=15%

Resultat-ENDR:
  Periode Jan–    , sats=0   ← nuller den uendelige perioden
  Periode Jan–Mar , sats=15% ← oppretter ny endelig periode
```

---

## kreditor_trekk_id-formatering

Feltet `kreditor_trekk_id` i Oppdrag Z er begrenset til **35 tegn**. Skatteetatens `trekkid` er en UUID (36 tegn med bindestreker).

| Situasjon                     | Løsning                                                |
|-------------------------------|--------------------------------------------------------|
| trekkid er UUID (36 tegn)     | Fjern bindestreker → 32 tegn, legg til `-P` eller `-M` |
| trekkid ≤ 34 tegn             | Bruk direkte med `-P`/`-M`-suffiks                     |
| trekkid > 34 tegn (ikke UUID) | BASE64-kodet kryptografisk hash, med `-P`/`-M`-suffiks |

Eksempel:
```
trekkid:             "550e8400-e29b-41d4-a716-446655440000"
kreditor_trekk_id:   "550e8400e29b41d4a716446655440000-P"  (prosenttrekk)
kreditor_trekk_id:   "550e8400e29b41d4a716446655440000-M"  (beløpstrekk)
```

---

## Avsluttede trekk (OPPH)

Når et trekk fra Skatteetaten har `trekkstatus = AVSLUTTET`:
- Det sendes **ingen** periodeoppdateringer til Oppdrag Z
- Oppdrag Z håndterer avslutning av trekket på sin side
- Trekket i databasen settes til `BEHANDLET`

---

## Datadrevne tester for transformasjonslogikken

`SkeEksemplerTest` kjører scenariebaserte tester fra JSON-filer i `src/test/resources/ske_trekkeksempler/`:

```
1.json        → 1_result.json        (nytt trekk, kun prosent)
1_1.json      → 1_1_result.json      (versjon 2, endret prosent)
1_2.json      → 1_2_result.json      (versjon 3, avsluttet)
2.json        → 2_result.json        (nytt trekk, kombinert P+M)
...
```

Testene kjøres sekvensielt slik at databasetilstand fra forrige steg er utgangspunktet for neste – akkurat som i produksjon.
