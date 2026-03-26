# Overordnet arkitektur – sokos-utleggstrekk

## Hva er sokos-utleggstrekk?

sokos-utleggstrekk er en **integrasjonstjeneste** som håndterer flyt av utleggstrekk (lønnstrekk) mellom Skatteetaten og NAVs utbetalingssystem (Oppdrag Z). Applikasjonen er en del av NAV Økonomilinjen og sørger for at arbeidsgivere trekker riktig beløp fra lønn til de borgere som har en gjeld hos myndighetene.

## Systemets rolle i NAVs økosystem

```
┌─────────────────┐        REST API (Maskinporten)       ┌───────────────────────────┐
│  Skatteetaten   │ ──────────────────────────────────►  │                           │
│                 │                                       │   sokos-utleggstrekk      │
│  Trekkpålegg   │                                       │                           │
└─────────────────┘                                       │  (GCP / NAIS)             │
                                                          │                           │
┌─────────────────┐            IBM MQ                    │                           │
│   Oppdrag Z     │ ◄──────────────────────────────────  │                           │
│  (utbetalings-  │                                       │                           │
│   system)       │ ──────────────────────────────────►  │                           │
│                 │          Kvitteringer                 └───────────────────────────┘
└─────────────────┘
```

## Ansvar

| Område | Beskrivelse |
|--------|-------------|
| **Henting** | Henter trekkpålegg fra Skatteetaten via REST API time for time |
| **Transformasjon** | Konverterer Skatteetatens format til Oppdrag Z sitt format |
| **Splitting** | Splitter trekk med kombinerte satser (prosent + beløp) i separate dokumenter |
| **Sending** | Sender innrapporteringstrekk til Oppdrag Z over IBM MQ |
| **Kvittering** | Mottar og lagrer kvitteringer fra Oppdrag Z |
| **Overvåking** | Rapporterer manglende kvitteringer og feil via Slack og Prometheus |

## Teknologistack

| Komponent | Teknologi |
|-----------|-----------|
| Programmeringsspråk | Kotlin (JVM 21) |
| Web-rammeverk | Ktor |
| Database | PostgreSQL (via HikariCP og JDBI) |
| Meldingskø | IBM MQ (via JMS) |
| Autentisering | Maskinporten (OAuth 2.0) |
| Migrasjon | Flyway |
| Feature toggles | Unleash |
| Metrics | Prometheus / Micrometer |
| Varsling | Slack Webhooks |
| Bygg | Gradle (Kotlin DSL) |
| Platform | NAIS / Google Cloud Platform |

## Miljøer

| Miljø | Kluster | Formål |
|-------|---------|--------|
| Lokal | – | Utvikling med proxy til dev-database |
| Dev | `dev-gcp` | Test og integrasjonstesting |
| Prod | `prod-gcp` | Produksjon |

## Skalerbarhet og tilgjengelighet

- Applikasjonen er en **stateless** mikrotjeneste (tilstand lagres i PostgreSQL)
- Deployment via NAIS sikrer automatisk skalering og selvhelbredelse
- Kubernetes liveness- og readiness-prober på `/internal/isAlive` og `/internal/isReady`
- Scheduled job kjøres én gang per time – ikke kritisk for sanntids tilgjengelighet
