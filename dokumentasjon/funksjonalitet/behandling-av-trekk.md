# Behandling av trekkpålegg

## Hva gjør denne funksjonen?

Behandlingsfunksjonen leser trekkpålegg med status `MOTTATT` fra databasen og transformerer dem til innrapporteringstrekk klare for sending til Oppdrag Z.

## Steg i behandlingen

### 1. Hent ubehandlede trekk

Alle trekk med status `MOTTATT` hentes fra databasen og behandles i rekkefølge.

### 2. Bestem aksjonskode

| Situasjon | Aksjonskode |
|-----------|-------------|
| Trekket finnes ikke i OS fra før | `NY` |
| Trekket er kjent i OS og har endrede perioder | `ENDR` |
| Trekket har `trekkstatus = AVSLUTTET` | `OPPH` (ingen perioder sendes) |

### 3. Splitt i P og M (hvis nødvendig)

Dersom trekkpålegget inneholder **begge** typer satser (prosenttrekk og beløpstrekk), lages det **to separate dokumenter**:

- **Prosenttrekk (P / LOPP):** Perioder med prosentstats. Perioder uten prosent nulles ut (sats=0).
- **Beløpstrekk (M / LOPM):** Perioder med beløpssats. Perioder uten beløp nulles ut (sats=0).

Dersom bare én type finnes, lages kun ett dokument.

### 4. Periodejustering til månedgrenser

Alle periodedatoer justeres til første/siste dag i måneden:
- `startdato` → 1. i måneden
- `sluttdato` → siste dag i måneden

Hvis to perioder overlapper etter justering, overstyrer den påfølgende perioden den forrige.

### 5. Nullering av fjernede perioder

Perioder som fantes i Oppdrag Z men ikke lenger er i det nye SKE-trekket, legges til med `sats=0`. Dette signaliserer til Oppdrag Z at perioden skal fjernes.

### 6. Lagre og sett status

Ferdigbehandlede dokumenter (`TransaksjonOS` + `PeriodeTilOS`) lagres i databasen. Trekkpålegget settes til `BEHANDLET`.

## Mulige utfall per trekkpålegg

| Utfall | Status | Forklaring |
|--------|--------|------------|
| Vellykket | `BEHANDLET` | 1–2 dokument(er) klar for sending |
| Feil under prosessering | `AVVIST` | Meldes til Slack, krever manuell handling |
| Nyere versjon finnes | `HOPPET_OVER` | Trekket ignoreres, nyere versjon behandles |

## Feature toggle

Denne funksjonen kan slås av/på med Unleash-toggle:
```
sokos-utleggstrekk.prosesser-utleggstrekk.enabled
```

> For detaljert beskrivelse av transformasjonslogikken, se [trekk-transformasjon.md](../detaljert/trekk-transformasjon.md).
