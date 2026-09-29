# Kom i gang

Denne guiden hjelper deg å sette opp utviklingsmiljøet og kjøre applikasjonen lokalt.

## Forutsetninger

| Verktøy | Versjon | Formål |
|---------|---------|--------|
| JDK | 25+ | Kotlin-kompilering og runtime |
| Gradle | (wrapper inkludert) | Bygg og avhengigheter |
| naisdevice | Siste | VPN-tilgang til NAV-infrastruktur |
| kubectl / nais CLI | Siste | For proxy og secrets |
| Docker | (valgfritt) | Testcontainers i tester |

## Oppsett steg for steg

### 1. Klon repoet

```bash
git clone https://github.com/navikt/sokos-utleggstrekk.git
cd sokos-utleggstrekk
```

### 2. Sett opp environment-variabler

Kjør setupLocalEnvironment-scriptet (krever naisdevice):

```bash
chmod 755 setupLocalEnvironment.sh && ./setupLocalEnvironment.sh
```

Dette oppretter filen `defaults.properties` med alle nødvendige environment-variabler. **Merk**: `POSTGRES_USERNAME` og `POSTGRES_PASSWORD` må hentes manuelt fra Vault.

### 3. Start database-proxy

For å koble til dev-databasen lokalt:

```bash
chmod 755 startProxy.sh && ./startProxy.sh
```

Dette starter en proxy mot Cloud SQL-instansen i dev.

### 4. Kjør applikasjonen

```bash
./gradlew run
```

Applikasjonen starter på port 8080. Helsesjekker er tilgjengelig på:
- Liveness: http://localhost:8080/internal/isAlive
- Readiness: http://localhost:8080/internal/isReady
- Metrikker: http://localhost:8080/internal/metrics

---

## Kjøre tester

```bash
./gradlew test
```

Testene bruker:
- **Testcontainers** for PostgreSQL (krever Docker)
- **Apache Artemis** som embedded MQ-broker
- **Ktor test-host** for HTTP-tester
- **MockK** for mocking

---

## Bygg

```bash
./gradlew build
```

Bygget inkluderer:
- `ktlintFormat` (formatering av Kotlin-kode)
- Kompilering
- Tester med Kover (code coverage)
- Pre-commit hook installasjon

---

## Prosjektstruktur (overordnet)

```
sokos-utleggstrekk/
├── .github/workflows/     # CI/CD GitHub Actions
├── .nais/                 # NAIS-konfigurasjon (dev/prod)
│   ├── dev/
│   │   ├── Dockerfile
│   │   ├── naiserator-dev.yaml
│   │   └── unleash-dev.yaml
│   └── prod/
├── dokumentasjon/         # Denne dokumentasjonen
├── src/
│   ├── main/
│   │   ├── kotlin/no/nav/sokos/utleggstrekk/
│   │   │   ├── Application.kt          # Main entry point
│   │   │   ├── api/                     # HTTP-endepunkter
│   │   │   ├── client/                  # HTTP-klienter (Skatteetaten, Slack)
│   │   │   ├── config/                  # Konfigurasjon og oppstart
│   │   │   ├── database/               # Repository og modeller
│   │   │   ├── domene/                  # Domenemodeller (ske + nav)
│   │   │   ├── metrics/                # Prometheus-metrikker
│   │   │   ├── mq/                     # IBM MQ producer/listener
│   │   │   ├── scheduling/             # Scheduler
│   │   │   ├── security/              # Maskinporten-autentisering
│   │   │   ├── service/               # Forretningslogikk
│   │   │   ├── unleash/              # Feature toggles
│   │   │   └── utils/                 # Hjelpefunksjoner
│   │   └── resources/
│   │       ├── application.conf        # Hovedkonfigurasjon
│   │       ├── application-{env}.conf  # Miljøoverstyrelser
│   │       ├── db/migration/           # Flyway-migrasjoner
│   │       └── ske_trekkeksempler/     # Eksempel-JSON fra SKE
│   └── test/
├── build.gradle.kts       # Bygg-konfigurasjon
└── gradlew                # Gradle wrapper
```

---

## Nyttige endepunkter i dev

| Formål | URL |
|--------|-----|
| Applikasjon | https://sokos-utleggstrekk.intern.dev.nav.no |
| Unleash | https://okonomi-unleash-web.iap.nav.cloud.nais.io/ |
| Grafana/Metrikker | Via NAIS-dashboards |
| Logs (Kibana) | Via nav.no/logs |

---

## Vanlige problemer

### `defaults.properties` mangler
Kjør `setupLocalEnvironment.sh` på nytt. Krever aktiv naisdevice-tilkobling.

### Database-tilkobling feiler
Sjekk at `startProxy.sh` kjører og at du har riktig brukernavn/passord fra Vault.

### MQ-tilkobling feiler lokalt
MQ-tilkobling krever nettverkstilgang via naisdevice. Lokalt kan du deaktivere MQ-sending ved å sette Unleash-toggle `sokos-utleggstrekk.send-til-os.enabled` til false.

### Tester feiler med Docker-feil
Testene krever Docker for Testcontainers (PostgreSQL). Sjekk at Docker kjører.

---

## Nyttige Gradle-kommandoer

| Kommando | Formål |
|----------|--------|
| `./gradlew run` | Start applikasjonen |
| `./gradlew test` | Kjør alle tester |
| `./gradlew ktlintFormat` | Formater kode |
| `./gradlew ktlintCheck` | Sjekk formatering |
| `./gradlew koverHtmlReport` | Generer coverage-rapport |
| `./gradlew dependencies` | Vis avhengighetstre |
| `./gradlew build` | Full bygg inkl. tester |
