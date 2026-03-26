# Overordnet dokumentasjon – sokos-utleggstrekk

Denne mappen inneholder overordnet dokumentasjon av sokos-utleggstrekk. Her beskrives systemet på et høyt nivå, uten å gå inn i tekniske detaljer.

## Innhold

| Fil | Beskrivelse |
|-----|-------------|
| [arkitektur.md](arkitektur.md) | Systemets overordnede arkitektur og plassering i NAVs økosystem |
| [flyt.md](flyt.md) | Overordnet forretningsflyt fra Skatteetaten til Oppdrag Z |
| [integrasjoner.md](integrasjoner.md) | Oversikt over alle eksterne integrasjoner |

## Kort sammendrag

sokos-utleggstrekk er en integrasjonstjeneste som henter utleggstrekk (lønnstrekk) fra **Skatteetaten** og videresender disse til **Oppdrag Z** (NAVs utbetalingssystem). Applikasjonen kjører på NAVs NAIS-plattform i Google Cloud (GCP) og prosesserer data time for time via en scheduler.
