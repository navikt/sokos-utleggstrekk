# Detaljert arkitektur – sokos-utleggstrekk

## Overordnet lagdeling

Applikasjonen er organisert i følgende lag, implementert i Kotlin med Ktor som rammeverk:

```
┌─────────────────────────────────────────────────────────┐
│  API-lag          NaisApi (helse/metrics-endepunkter)   │
├─────────────────────────────────────────────────────────┤
│  Scheduler        UtleggsTrekkScheduler                 │
├─────────────────────────────────────────────────────────┤
│  Service-lag      UtleggsTrekkService  (orkestrering)   │
│                   BehandleTrekkService (transformasjon)  │
│                   SlackService         (varsling)        │
├─────────────────────────────────────────────────────────┤
│  Integrasjonslag  SkeClient            (REST til SKE)   │
│                   JmsProducerService   (MQ-sending)      │
│                   JmsListenerService   (MQ-mottak)       │
├─────────────────────────────────────────────────────────┤
│  Datalaget        Repository           (PostgreSQL)      │
│                   Flyway-migrasjonar                     │
├─────────────────────────────────────────────────────────┤
│  Sikkerhet/konfig MaskinportenClient   (OAuth 2.0)      │
│                   PropertiesConfig                       │
│                   MQConfig                               │
├─────────────────────────────────────────────────────────┤
│  Tverrgående      UnleashIntegration   (feature toggles)│
│                   MetricsService       (Prometheus)      │
└─────────────────────────────────────────────────────────┘
```

## Kodestruktur

```
src/main/kotlin/.../sokos/utleggstrekk/
├── Application.kt                      # Innganspunkt, Ktor-konfigurasjon
├── api/
│   └── NaisApi.kt                      # /internal/isAlive, /internal/isReady, /internal/metrics
├── client/
│   ├── SkeClient.kt                    # REST-klient mot Skatteetaten
│   └── HttpClientBuilder.kt            # Felles HTTP-klientfabrikk
├── config/
│   ├── PropertiesConfig.kt             # Laster applikasjonskonfigurasjon
│   └── RoutingConfig.kt                # Ktor routing-oppsett
├── database/
│   ├── PostgresDataSource.kt           # HikariCP-oppsett, Flyway-kjøring
│   ├── Repository.kt                   # Alle databasespørringer
│   └── model/
│       ├── TrekkFraSkatt.kt            # DB-modell for SKE-trekk
│       ├── TransaksjonOS.kt            # DB-modell for OS-transaksjoner
│       └── PeriodeTilOS.kt             # DB-modell for OS-perioder
├── domene/
│   ├── ske/                            # Dataklasser fra Skatteetaten
│   │   ├── Trekkpaalegg.kt
│   │   ├── TrekkstorrelseForPeriode.kt
│   │   └── Betalingsinformasjon.kt
│   └── nav/                            # Dataklasser til Oppdrag Z
│       ├── TrekkTilOppdrag.kt
│       ├── InnrapporteringTrekk.kt
│       ├── Aksjonskode.kt
│       └── TrekkAlternativ.kt
├── metrics/
│   └── MetricsService.kt               # Prometheus-målinger
├── mq/
│   ├── JmsProducerService.kt           # Sender meldinger til MQ
│   ├── JmsListenerService.kt           # Lytter på kvitteringskø
│   └── MQConfig.kt                    # IBM MQ-tilkoblingskonfigurasjon
├── scheduling/
│   └── UtleggsTrekkScheduler.kt        # Trigger service én gang per time
├── security/
│   └── MaskinportenAccessTokenClient.kt # OAuth 2.0-token via Maskinporten
├── service/
│   ├── UtleggsTrekkService.kt          # Hoved-orkestrering
│   ├── BehandleTrekkService.kt         # Transformasjon SKE → OS
│   └── SlackService.kt                 # Slack-varsling
└── unleash/
    └── UnleashIntegration.kt           # Feature toggle-integrasjon

src/main/resources/
├── application.conf                    # Hoved-konfigurasjon (HOCON)
├── application-local.conf              # Lokal overriding
├── application-dev.conf                # Dev-miljø
├── application-prod.conf               # Prod-miljø
├── logback.xml                         # Loggkonfigurasjon
└── db/migration/                       # Flyway-SQL-migrasjonar
    ├── V1.0.0__create_tables.sql
    ├── V1.0.1__create_index.sql
    └── ...
```

## Konfigurasjon

Konfigurasjon håndteres via **HOCON** (`application.conf`) og miljøvariabler. Strukturen er:

```hocon
application {
  appName = "sokos-utleggstrekk"
  scheduler {
    isActive = true          # SCEDULER_ACTIVE
    minutes = 45             # SCEDULER_MINUTES – minutt på timen for kjøring
  }
}

maskinportenClientConfig {
  clientId = ${MASKINPORTEN_CLIENT_ID}
  wellKnownUrl = ${MASKINPORTEN_WELL_KNOWN_URL}
  scopes = ${MASKINPORTEN_SCOPES}
  rsaKeyString = ${MASKINPORTEN_CLIENT_JWK}
}

mqProperties {
  hostname = ${MQ_HOSTNAME}
  port = ${MQ_PORT}
  channel = ${MQ_CHANNEL}
  queueManagerName = ${MQ_QUEUE_MANAGER_NAME}
  queueName = ${MQ_QUEUE_NAME}
  replyQueueName = ${MQ_REPLY_QUEUE_NAME}
  username = ${MQ_USERNAME}
  password = ${MQ_PASSWORD}
}

postgresConfig {
  host = ${POSTGRES_HOST}
  port = ${POSTGRES_PORT}
  jdbcUrl = ${POSTGRES_JDBC_URL}
  username = ${POSTGRES_USERNAME}
  password = ${POSTGRES_PASSWORD}
}
```

## Databaseskjema

Administrert av **Flyway**. Se [datamodell](../datamodell/README.md) for full ER-diagram.

| Tabell | Beskrivelse |
|--------|-------------|
| `fraskatt` | Trekkpålegg mottatt fra Skatteetaten |
| `fraskatt_status` | Statussporing per trekkpålegg |
| `periode_fra_skatt` | Perioder for hvert trekkpålegg |
| `betalingsinformasjon_fraskatt` | Betalingsinformasjon per trekkpålegg |
| `transaksjon_os` | Innrapporteringstrekk sendt til Oppdrag Z |
| `periode_til_os` | Perioder i hvert OS-dokument |
| `kvittering_fra_os` | Kvitteringer mottatt fra Oppdrag Z |

## Teststruktur

```
src/test/kotlin/
├── testcases/
│   ├── SkeEksemplerTest.kt         # Datadrevne transformasjonstester
│   └── PeriodejusteringsTest.kt    # Periodejusteringstester
├── database/
│   ├── RepositoryTest.kt           # Integrasjonstester mot PostgreSQL
│   └── model/RemapFomTomTest.kt    # Dato-justering enhetstester
├── mq/
│   └── JmsListenerServiceTest.kt   # MQ-kvitteringstester
└── util/
    ├── TestContainer.kt            # Docker Testcontainers (PostgreSQL + Artemis MQ)
    ├── TestData.kt                 # Testfiksturer
    └── TestFileTools.kt            # JSON-filhjelper for testcase-filer
```

**Testverktøy:**
- **Kotest** – BDD-testrammeverk
- **Testcontainers** – Docker-baserte integrasjonstester (PostgreSQL, Apache Artemis MQ)
- **Kover** – Kotlin kodedekning (`./gradlew koverHtmlReport`)

**Kjøre tester:**
```bash
./gradlew test                                # Alle tester
./gradlew test --tests "*SkeEksemplerTest*"  # Spesifikk klasse
./gradlew koverHtmlReport                    # Generer dekningsrapport
```
