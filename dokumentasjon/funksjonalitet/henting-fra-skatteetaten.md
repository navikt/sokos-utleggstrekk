# Henting fra Skatteetaten

## Hva gjør denne funksjonen?

Applikasjonen henter løpende nye trekkpålegg fra Skatteetatens REST API. Hentingen trigges av scheduleren og er den første fasen i prosesseringssyklusen.

## Hvordan fungerer det?

### Sekvensnummer-basert paginering

Skatteetaten tildeler hvert trekkpålegg et stigende **sekvensnummer**. sokos-utleggstrekk husker det høyeste sekvensnummeret den har mottatt, og spør alltid om trekk som er nyere enn dette:

```
GET /api/trekkpaalegg/v1?fraSekvens=<siste_kjente>&antall=2500
```

Første gang applikasjonen kjøres (eller etter datatap) settes `fraSekvens=0`, som returnerer gjeldende versjon av alle aktive trekk.

### Autentisering

Kallet autentiseres med et **Maskinporten Bearer-token** hentet på vegne av NAV Økonomilinjen (org.nr 995277670). Tokenet caches og fornyes automatisk før det utløper.

> Se [integrasjoner](../overordnet/integrasjoner.md#skatteetaten) for mer om systembruker og Maskinporten-oppsett.

### Lagring i database

Hvert mottatt `Trekkpaalegg` lagres i databasen:

| Tabell                          | Innhold                             |
|---------------------------------|-------------------------------------|
| `fraskatt`                      | Hoved-data for trekkpålegget        |
| `fraskatt_status`               | Settes til `MOTTATT`                |
| `periode_fra_skatt`             | Alle perioder for trekket           |
| `betalingsinformasjon_fraskatt` | KID, kontonummer, betalingsmottaker |

Det siste sekvensnummeret oppdateres etter at alle trekk er lagret.

## Kanttilfeller

| Situasjon                                | Håndtering                                                                                                                      |
|------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------|
| SKE API er utilgjengelig                 | Hentingen avbrytes, neste syklus prøver igjen                                                                                   |
| Samme trekk mottas igjen (samme versjon) | Lagres ikke på nytt (duplikat-sjekk på sekvensnummer)                                                                           |
| Nyere versjon av eksisterende trekk      | Lagres som ny rad – eldre versjon beholdes slik at behandlingen kan finne hvilke perioder som er endret, lagt til eller fjernet |
| Trekk med `trekkstatus = AVSLUTTET`      | Lagres med `MOTTATT` og behandles videre                                                                                        |

## Feature toggle

Denne funksjonen kan slås av/på med Unleash-toggle:
```
sokos-utleggstrekk.hent-fra-ske.enabled
```
