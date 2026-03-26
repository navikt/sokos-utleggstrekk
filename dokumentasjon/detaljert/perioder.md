# Periodebehandling – sokos-utleggstrekk

Dette dokumentet forklarer i detalj hvordan perioder fra Skatteetaten utledes, transformeres og sendes til Oppdrag Z.

---

## Datamodeller for perioder

### Innkommende perioder fra Skatteetaten (`PeriodeFraSkatt`)

```kotlin
data class PeriodeFraSkatt(
    val fraSkattID: Long,         // Fremmednøkkel til trekkpålegget
    val trekkIdSke: String,
    val startdato: String,        // Vilkårlig dato (YYYY-MM-DD)
    val sluttdato: String?,       // null = åpen (uendelig) periode
    val trekkbeloep: Double?,     // Beløp i NOK – finnes hvis trekket er et beløpstrekk (LOPM)
    val trekkprosent: Double?,    // Prosentstats – finnes hvis trekket er et prosenttrekk (LOPP)
)
```

En periode kan ha `trekkbeloep`, `trekkprosent`, eller begge. Hvilken type perioden tilhører bestemmes av:

```kotlin
fun trekkAlternativ(): TrekkAlternativ =
    when {
        trekkprosent != null -> LOPP   // Prosenttrekk
        trekkbeloep != null -> LOPM    // Beløpstrekk (månedssats)
        else -> feil
    }
```

### Utgående perioder til Oppdrag Z (`PeriodeTilOS`)

```kotlin
data class PeriodeTilOS(
    val osTransaksjonId: Long,
    val sats: Double,              // Beløp eller prosent (avhengig av alternativ)
    val periodeFomDato: String,    // Alltid 1. i måneden (etter justering)
    val periodeTomDato: String?,   // Alltid siste dag i måneden, eller null
)
```

---

## Steg 1 – Periodejustering til månedgrenser (`mapNewFomTom`)

Oppdrag Z krever at perioder starter på **1. i måneden** og slutter på **siste dag i måneden**. Skatteetaten leverer vilkårlige datoer. Funksjonen `mapNewFomTom()` justerer alle perioder.

### Algoritme

Periodene behandles i **omvendt kronologisk rekkefølge** (nyeste først):

1. Juster `sluttdato` til siste dag i måneden (f.eks. `2025-02-14` → `2025-02-28`)
2. Juster `startdato` til 1. i måneden (f.eks. `2025-01-17` → `2025-01-01`)
3. Hvis den justerte startdatoen overlapper med en allerede behandlet periode, avkortes forrige periode til dagen **før** den nye startet

```kotlin
fun List<PeriodeFraSkatt>.mapNewFomTom(): List<PeriodeFraSkatt> {
    var maxTom: LocalDate? = null
    val reversed = sortedByDescending { it.startdato }

    for (periode in reversed) {
        val justertTom = periode.sluttdato?.let {
            LocalDate.parse(it).withDayOfMonth(LocalDate.parse(it).lengthOfMonth())
        }
        val nyTom = if (maxTom != null && (justertTom == null || justertTom.isAfter(maxTom))) maxTom else justertTom
        val nyFom = LocalDate.parse(periode.startdato).withDayOfMonth(1)

        if (maxTom == null || nyFom.isBefore(maxTom)) {
            maxTom = nyFom.minusDays(1)  // Neste periode kan ikke gå forbi denne datoen
            // Legg til justert periode
        }
    }
}
```

### Eksempler

**Enkelt tilfelle – én periode:**
```
SKE:  startdato=2025-01-17, sluttdato=2025-03-14
 ↓
OS:   periodeFomDato=2025-01-01, periodeTomDato=2025-03-31
```

**To perioder i samme måned – overlap løses:**
```
SKE:  Periode A: startdato=2025-01-05, sluttdato=2025-01-20
      Periode B: startdato=2025-01-21

Etter justering ville begge starte 2025-01-01.
Periode A avkortes til å slutte 2025-01-31 (siste i januar).
Periode B overstyrer: starter 2025-01-01 (Periode A erstattes).

OS:   Periode A: 2025-01-01 til 2025-01-31  ← avkortet
      Periode B: 2025-01-01 →               ← overstyrer A
```

**Overlappende måneder – påfølgende periode avkorter forrige:**
```
SKE:  Periode A: startdato=2025-02-10, sluttdato=2025-03-06
      Periode B: startdato=2025-03-07

Periode A justert tom: 2025-03-31 – men Periode B starter 2025-03-01.
Periode A avkortes til siste dag i februar: 2025-02-28.

OS:   Periode A: 2025-02-01 til 2025-02-28
      Periode B: 2025-03-01 →
```

---

## Steg 2 – Splitting i trekk-alternativ (LOPM og LOPP)

Skatteetaten kan levere et trekkpålegg der **ulike perioder har ulik type** (noen med prosent, noen med beløp). Oppdrag Z behandler disse som separate trekk.

sokos-utleggstrekk lager **ett dokument per trekk-alternativ** (LOPM og/eller LOPP) som finnes i trekkpålegget.

### Regler

| Situasjon | Resultat |
|-----------|----------|
| Alle perioder har prosent | Kun ett P-dokument (LOPP) |
| Alle perioder har beløp | Kun ett M-dokument (LOPM) |
| Blandede perioder (noen P, noen M) | To dokumenter – ett P og ett M |
| Trekk er kjent i OS med begge alternativ | Begge alternativ videreføres |

### Satser for hvert alternativ

For en periode som kun har én type sats, settes den andre til 0 i det tilhørende dokumentet:

```kotlin
fun satsFor(alternativ: TrekkAlternativ): Double =
    if (alternativ == LOPM) trekkbeloep ?: 0.0
    else                    trekkprosent ?: 0.0
```

**Eksempel – blandet trekk:**
```
SKE:
  Periode 1: Jan–Mar, trekkprosent=15%
  Periode 2: Apr→   , trekkbeloep=2000kr

Resultat P-dokument (LOPP):
  Periode 1: 2025-01-01 – 2025-03-31, sats=15%
  Periode 2: 2025-04-01 →           , sats=0%   ← ingen prosent → nulleres

Resultat M-dokument (LOPM):
  Periode 1: 2025-01-01 – 2025-03-31, sats=0kr  ← ingen beløp → nulleres
  Periode 2: 2025-04-01 →           , sats=2000kr
```

---

## Steg 3 – Sammenligning med kjente OS-perioder

For å unngå å sende unødvendige oppdateringer til Oppdrag Z, sammenlignes nye perioder med det som allerede er sendt (og bekreftet med OK-kvittering).

### Hva hentes fra databasen

Kun perioder som tilhører transaksjoner med:
- `transaksjon_status = SENDT`
- `kvittering_status IN (OK, IKKE_MOTTATT)`

### Foreldede perioder filtreres vekk (`obsoleted`)

En OS-periode regnes som foreldet og ignoreres dersom:

| Betingelse | Forklaring |
|------------|------------|
| `sats == 0.0` | Perioden er allerede nullert |
| `periodeTomDato` er passert | Perioden er utløpt |
| Det finnes en nyere rad med samme fom/tom og `sats=0.0` | Perioden er overstyrt av en nyere nullering |

### Likhetsbetingelse

En SKE-periode regnes som **identisk** med en OS-periode kun hvis alle tre stemmer:

```kotlin
fun sameAs(periodeTilOS: PeriodeTilOS): Boolean =
    startdato == periodeTilOS.periodeFomDato &&
    sluttdato == periodeTilOS.periodeTomDato &&
    satsFor(trekkAlternativ()) == periodeTilOS.sats
```

Perioder som er identiske med noe OS allerede kjenner, sendes **ikke** på nytt.

---

## Steg 4 – Nullering av fjernede perioder

Perioder som finnes i Oppdrag Z fra tidligere, men som **ikke lenger er i det nye SKE-trekket**, må eksplisitt kanselleres. Dette gjøres ved å sende dem på nytt med `sats = 0.0`.

**Eksempel:**

```
Tidligere sendt til OS:
  Periode Jan–Mar, sats=1000kr
  Periode Apr→   , sats=2000kr

Nytt SKE-trekk inneholder kun:
  Periode Apr→   , sats=3000kr   (ny sats)

Resultat ENDR-dokument:
  sats=0.0,  Jan–Mar     ← nullering – perioden finnes ikke lenger
  sats=0.0,  Apr→        ← nullering – satsen er endret, gammel versjon kanselleres
  sats=3000, Apr→        ← ny periode med oppdatert sats
```

### Spesialtilfelle – uendelig periode som får sluttdato

En periode uten sluttdato som nå får en sluttdato, behandles som **to** oppdateringer: én som nuller den gamle uendelige perioden, og én som oppretter den nye avgrensede perioden.

```
Gammel OS:  Jan→    , sats=15%  (ingen sluttdato)
Nytt SKE:   Jan–Mar , sats=15%  (sluttdato satt)

Resultat ENDR-dokument:
  sats=0,   Jan→     ← nullerer den uendelige perioden
  sats=15%, Jan–Mar  ← oppretter endelig periode
```

---

## Steg 5 – Aksjonskode per alternativ

Aksjonskoden bestemmes separat for hvert trekk-alternativ (LOPM/LOPP):

| Alternativet i OS? | trekkstatus | Aksjonskode | Perioder sendes? |
|--------------------|-------------|-------------|-----------------|
| Nei | aktiv | `NY` | Ja |
| Ja | aktiv | `ENDR` | Ja (kun endringer + nulleringer) |
| Ja | avsluttet | `OPPH` | Nei |

---

## Komplett eksempel – endring fra beløp til prosent

### SKE versjon 1 (nytt trekk)
```json
{
  "trekkversjon": 1,
  "trekkstoerrelseForPeriode": [
    { "startdato": "2025-11-15", "trekkbeloep": { "trekkbeloep": 1000.0 } }
  ]
}
```
**Sendes til OS:**
```
NY, LOPM: [{ fom=2025-11-01, tom=null, sats=1000 }]
```

---

### SKE versjon 2 (bytt fra beløp til prosent midtveis)
```json
{
  "trekkversjon": 2,
  "trekkstoerrelseForPeriode": [
    { "startdato": "2025-11-15", "sluttdato": "2025-12-04", "trekkbeloep": { "trekkbeloep": 1000.0 } },
    { "startdato": "2025-12-05", "trekkprosent": { "trekkprosent": 15.0 } }
  ]
}
```

**Etter `mapNewFomTom`:**
```
Periode A: 2025-11-01 – 2025-11-30, beløp=1000
Periode B: 2025-12-01 →           , prosent=15%
```

**Sammenlignet med OS:**
- Periode A (LOPM): delvis kjent (fom=2025-11-01 men tom var null) → nuller gammel, sender ny med tom
- Periode B (LOPM): ikke kjent → sender med sats=0 (perioden finnes kun som LOPP)
- Periode B (LOPP): ikke kjent i OS → aksjonskode NY

**Sendes til OS:**
```
ENDR, LOPM:
  { fom=2025-11-01, tom=null,       sats=0    }  ← nullerer gammel uendelig periode
  { fom=2025-11-01, tom=2025-11-30, sats=1000 }  ← ny endelig periode
  { fom=2025-12-01, tom=null,       sats=0    }  ← desember har ikke beløp

NY, LOPP:
  { fom=2025-11-01, tom=2025-11-30, sats=0    }  ← november har ikke prosent
  { fom=2025-12-01, tom=null,       sats=15   }  ← ny prosentperiode
```

---

## Oppsummering av periodelogikken

```
SKE-perioder
    │
    ▼
mapNewFomTom()          → Juster fom til 1. i mnd, tom til siste dag i mnd
                           Avkort foregående periode ved overlapp
    │
    ▼
Bestem alternativ       → Finnes LOPM? LOPP? Begge?
                           Inkluder alternativ fra tidligere OS-transaksjoner
    │
    ▼
Hent OS-perioder        → Kun SENDT + (OK eller IKKE_MOTTATT)
Filtrer foreldede       → Fjern nullerte, utløpte og overstyrte
    │
    ▼
Finn fjernede perioder  → OS-perioder som ikke lenger finnes i SKE
Lag nulleringer         → Legg til med sats=0.0
    │
    ▼
Finn nye perioder       → SKE-perioder ikke allerede kjent av OS
Legg til nye perioder   → Med riktig sats per alternativ (0 hvis ikke aktuelt)
    │
    ▼
Lag dokument(er)        → Ett per alternativ, med riktig aksjonskode (NY/ENDR/OPPH)
```
