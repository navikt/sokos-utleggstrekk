# Kode – sokos-utleggstrekk

Gjennomgang av de viktigste klassene og tjenestene i applikasjonen.

---

## Application.kt

Innganspunktet for applikasjonen. Starter Ktor-serveren og setter opp alle avhengigheter:

- Initialiserer `PostgresDataSource` og kjører Flyway-migrasjonar
- Setter opp `MQConfig` (IBM MQ-tilkobling)
- Starter `JmsListenerService` (MQ-lytter)
- Registrerer `NaisApi` (helse-endepunkter)
- Starter `UtleggsTrekkScheduler`

---

## UtleggsTrekkService

**Fil:** `service/UtleggsTrekkService.kt`  
**Ansvar:** Hoved-orkestrator for hele prosesseringssyklusen.

### Metoder

| Metode                       | Beskrivelse                                            |
|------------------------------|--------------------------------------------------------|
| `schedule()`                 | Innganspunkt kalt av scheduler. Koordinerer alle steg. |
| `lagreAlleNyeUtleggstrekk()` | Henter og lagrer nye trekkpålegg fra SKE               |
| `processTrekkpaalegg()`      | Validerer og lagrer mottatte trekk i DB                |
| `calculateMetrics()`         | Oppdaterer Prometheus-metrics                          |
| `reportMissingKvittering()`  | Rapporterer manglende kvitteringer til Slack           |

### Avhengigheter

```
UtleggsTrekkService
├── SkeClient               (henter fra SKE)
├── BehandleTrekkService    (transformerer trekk)
├── JmsProducerService      (sender til OS)
├── Repository              (databasetilgang)
├── SlackService            (varsling)
├── MetricsService          (metrics)
└── UnleashIntegration      (feature toggles)
```

---

## BehandleTrekkService

**Fil:** `service/BehandleTrekkService.kt`  
**Ansvar:** Transformerer trekkpålegg fra SKE-format til Oppdrag Z-format. Se [trekk-transformasjon.md](trekk-transformasjon.md) for detaljert beskrivelse av logikken.

### Metoder

| Metode                       | Beskrivelse                                    |
|------------------------------|------------------------------------------------|
| `behandleTrekk()`            | Behandler alle MOTTATT-trekk i databasen       |
| `lagTrekkDokument(trekk)`    | Lager ett eller to OS-dokumenter for et trekk  |
| `nyePerioderTilOS(...)`      | Beregner perioder som skal sendes til OS       |
| `nullerFjernetPerioder(...)` | Legger til null-perioder for fjernede perioder |

---

## SkeClient

**Fil:** `client/SkeClient.kt`  
**Ansvar:** HTTP-klient mot Skatteetatens REST API.

### Nøkkelatferd

- Henter Maskinporten-token via `MaskinportenAccessTokenClient`
- Kaller `GET /api/trekkpaalegg/v1?fraSekvens={n}&antall=2500`
- Paginerer automatisk ved store datamengder
- Returnerer `List<Trekkpaalegg>`

---

## JmsProducerService

**Fil:** `mq/JmsProducerService.kt`  
**Ansvar:** Sender `TrekkTilOppdrag`-meldinger som JSON over IBM MQ.

- Serialiserer `TrekkTilOppdrag` til JSON
- Sender til kø `MQ_QUEUE_NAME`
- Setter `JMSCorrelationID` til `transaksjonsId` for sporbarhet

---

## JmsListenerService

**Fil:** `mq/JmsListenerService.kt`  
**Ansvar:** Lytter på kvitteringskøen fra Oppdrag Z asynkront.

- Lytter på `MQ_REPLY_QUEUE_NAME`
- Deserialiserer kvitteringer (`TrekkTilOppdrag` med `mmel`-felt)
- Oppdaterer `kvittering_status` i `transaksjon_os`
- Lagrer `nav_trekk_id` ved suksess
- Lagrer feilkode og beskrivelse ved feil
- Sender Slack-varsel ved feil

---

## Repository

**Fil:** `database/Repository.kt`  
**Ansvar:** All database-tilgang (JDBI med PostgreSQL).

### Viktige metoder

| Metode                          | Beskrivelse                             |
|---------------------------------|-----------------------------------------|
| `lagreTrekkFraSkatt(trekk)`     | Lagrer nytt trekkpålegg                 |
| `hentMottatteTrekk()`           | Henter trekk med status MOTTATT         |
| `settTrekkStatus(id, status)`   | Oppdaterer status på et trekk           |
| `lagreTransaksjonOS(trans)`     | Lagrer ny OS-transaksjon                |
| `hentIkkeSendteTransaksjoner()` | Henter transaksjoner klar for sending   |
| `oppdaterKvitteringStatus(...)` | Lagrer kvitteringsresultat fra OS       |
| `hentSisteSekvens()`            | Returnerer siste mottatte sekvensnummer |

---

## Domenemodeller

### SKE-domene

```kotlin
data class Trekkpaalegg(
    val trekkid: String,                          // Unik ID (UUID)
    val sekvensnummer: Int,                       // Brukes til paginering
    val trekkversjon: Int,                        // Versjonsnummer
    val trekkstatus: Trekkstatus,                 // AKTIV | AVSLUTTET
    val trekkpliktig: String,                     // Org.nr (9 siffer)
    val skyldner: String,                         // Fødselsnummer (11 siffer)
    val trekkstoerrelseForPeriode: List<TrekkstorrelseForPeriode>,
    val betalingsinformasjon: Betalingsinformasjon
)

data class TrekkstorrelseForPeriode(
    val startdato: String,
    val sluttdato: String? = null,
    val trekkbeloep: Trekkbeloep? = null,        // Fast beløp (M)
    val trekkprosent: Trekkprosent? = null       // Prosentsats (P)
)
```

### NAV-domene (til Oppdrag Z)

```kotlin
data class TrekkTilOppdrag(
    val dokument: Document,
    val mmel: Mmel? = null                        // Kvitteringsfelt fra OS
)

data class InnrapporteringTrekk(
    val aksjonskode: Aksjonskode,                 // NY | ENDR | OPPH
    val kreditorTrekkId: String,                  // trekkid + "-P" eller "-M"
    val kodeTrekkAlternativ: TrekkAlternativ,     // LOPM (M) | LOPP (P)
    val debitorId: String,
    val kid: String,
    val perioder: Perioder?
)

enum class Aksjonskode { NY, ENDR, OPPH }
enum class TrekkAlternativ { LOPM, LOPP }         // Beløp (M) | Prosent (P)
```

### Databasemodeller

```kotlin
data class TrekkFraSkatt(
    val id: Long,
    val trekkid: String,
    val sekvensnummer: Int,
    val trekkversjon: Int,
    val trekkstatus: String
)

enum class SkattTrekkStatus {
    MOTTATT, BEHANDLET, AVVIST, REPETERES, HOPPET_OVER
}

data class TransaksjonOS(
    val id: Long,
    val transaksjonsID: String,
    val trekkIdSke: String,
    val navTrekkId: String,
    val transaksjonStatus: TransaksjonsStatus,    // IKKE_SENDT | SENDT
    val kvitteringStatus: KvitteringStatus,       // IKKE_MOTTATT | OK | FEIL | UKJENT
    val aksjonskode: Aksjonskode,
    val dokumentJson: String                      // Rå JSON sendt til OS
)
```

---

## MaskinportenAccessTokenClient

**Fil:** `security/MaskinportenAccessTokenClient.kt`  
**Ansvar:** Henter og cacher OAuth 2.0-tokens fra Maskinporten.

- Bruker privat RSA-nøkkel (JWK) for å signere JWT-grant
- Veksler JWT-grant mot Bearer-token hos Maskinporten
- Cacher token til det er i ferd med å utløpe

---

## MetricsService

**Fil:** `metrics/MetricsService.kt`  
**Ansvar:** Prometheus-metrics eksponert på `/internal/metrics`.

Eksempler på målinger:
- Antall trekk per `SkattTrekkStatus`
- Antall transaksjoner per `KvitteringStatus`
- Antall manglende kvitteringer

---

## SlackService

**Fil:** `service/SlackService.kt`  
**Ansvar:** Samler og sender Slack-varsler via webhook.

- Bufrer feilmeldinger under prosessering
- Sender samlet varsel til slutt (unngår spam)
- Konfigureres via `SOKOS_UTLEGGSTREKK_SLACK_WEBHOOK_URL`

---

## NaisApi

**Fil:** `api/NaisApi.kt`  
**Ansvar:** Interne Kubernetes-endepunkter.

| Endepunkt               | Formål                     |
|-------------------------|----------------------------|
| `GET /internal/isAlive` | Kubernetes liveness probe  |
| `GET /internal/isReady` | Kubernetes readiness probe |
| `GET /internal/metrics` | Prometheus metrics-scrape  |
