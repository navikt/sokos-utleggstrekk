# Detaljert flyt – sokos-utleggstrekk

## Innganspunkt: Scheduler

`UtleggsTrekkScheduler` trigges én gang per time (på minuttet satt i `SCEDULER_MINUTES`) og kaller `UtleggsTrekkService.schedule()`.

```kotlin
// UtleggsTrekkScheduler – forenklet
fun startScheduler() {
    coroutineScope.launch {
        while (true) {
            val now = LocalDateTime.now()
            if (now.minute == schedulerMinutes) {
                utleggsTrekkService.schedule()
            }
            delay(60_000)
        }
    }
}
```

Feature toggle `sokos-utleggstrekk.hent-fra-ske.enabled` / `prosesser-utleggstrekk.enabled` / `send-til-os.enabled` styrer hva som faktisk kjøres.

---

## Steg 1: Hent trekkpålegg fra Skatteetaten

```mermaid
sequenceDiagram
    participant Scheduler
    participant UtleggsTrekkService
    participant SkeClient
    participant DB

    Scheduler->>UtleggsTrekkService: schedule()
    UtleggsTrekkService->>DB: Hent siste sekvensnummer
    DB-->>UtleggsTrekkService: sekvensnummer = n
    UtleggsTrekkService->>SkeClient: hentTrekkpaalegg(fraSekvens=n)
    SkeClient->>Maskinporten: Hent tilgangstoken
    Maskinporten-->>SkeClient: Bearer token
    SkeClient->>SKE API: GET /trekkpaalegg/v1?fraSekvens=n
    SKE API-->>SkeClient: Liste med Trekkpaalegg (maks 2500)
    SkeClient-->>UtleggsTrekkService: List<Trekkpaalegg>
    UtleggsTrekkService->>DB: Lagre hvert trekk (status=MOTTATT)
    UtleggsTrekkService->>DB: Oppdater siste sekvensnummer
```

**Detaljer:**
- `SkeClient` håndterer paginering automatisk (henter inntil 2 500 per kall)
- Token caches i Maskinporten-klienten mellom kall
- Hvert mottatt `Trekkpaalegg` lagres som `TrekkFraSkatt` i `fraskatt`-tabellen
- `fraskatt_status` settes til `MOTTATT`

---

## Steg 2: Behandle trekkpålegg (BehandleTrekkService)

```mermaid
sequenceDiagram
    participant UtleggsTrekkService
    participant BehandleTrekkService
    participant DB

    UtleggsTrekkService->>DB: Hent alle trekk med status MOTTATT
    DB-->>UtleggsTrekkService: List<TrekkFraSkatt>
    loop For hvert trekkpålegg
        UtleggsTrekkService->>BehandleTrekkService: behandleTrekk(trekkpaalegg)
        BehandleTrekkService->>DB: Finnes trekket i OS fra før?
        alt Nytt trekk
            BehandleTrekkService->>BehandleTrekkService: Sett aksjonskode = NY
        else Kjent trekk
            BehandleTrekkService->>DB: Hent eksisterende perioder fra OS
            BehandleTrekkService->>BehandleTrekkService: Beregn diff (ENDR/OPPH)
        end
        BehandleTrekkService->>BehandleTrekkService: Lag dokument(er) (P og/eller M)
        BehandleTrekkService->>DB: Lagre TransaksjonOS + PeriodeTilOS
        BehandleTrekkService->>DB: Sett trekk til BEHANDLET
    end
```

**Tilstandsmaskin for behandling:**

| Situasjon | Aksjonskode | Forklaring |
|-----------|-------------|------------|
| Trekket finnes ikke i OS | `NY` | Første gang dette trekket sendes |
| Trekket finnes i OS, perioder er endret | `ENDR` | Oppdaterer eksisterende trekk |
| Trekket er avsluttet i SKE | `OPPH` | Ingen nye periodeoppdateringer sendes |

---

## Steg 3: Send til Oppdrag Z via MQ

```mermaid
sequenceDiagram
    participant UtleggsTrekkService
    participant JmsProducerService
    participant DB
    participant OppdragZ

    UtleggsTrekkService->>DB: Hent TransaksjonOS med status IKKE_SENDT
    DB-->>UtleggsTrekkService: Liste med dokumenter
    loop For hvert dokument
        UtleggsTrekkService->>JmsProducerService: sendMelding(TrekkTilOppdrag)
        JmsProducerService->>OppdragZ: IBM MQ (JSON-melding)
        JmsProducerService-->>UtleggsTrekkService: OK
        UtleggsTrekkService->>DB: Sett transaksjon_status = SENDT
    end
```

---

## Steg 4: Motta kvitteringer (asynkront)

`JmsListenerService` lytter kontinuerlig på kvitteringskøen og prosesserer meldinger uavhengig av scheduler-syklusen.

```mermaid
sequenceDiagram
    participant OppdragZ
    participant JmsListenerService
    participant DB
    participant Slack

    OppdragZ->>JmsListenerService: Kvittering (JSON over MQ)
    JmsListenerService->>JmsListenerService: Deserialiser TrekkTilOppdrag
    alt Kvittering OK
        JmsListenerService->>DB: Lagre nav_trekk_id
        JmsListenerService->>DB: Sett kvittering_status = OK
    else Kvittering FEIL
        JmsListenerService->>DB: Lagre feilkode + beskrivelse
        JmsListenerService->>DB: Sett kvittering_status = FEIL
        JmsListenerService->>Slack: Send feilvarsel
    end
```

---

## Steg 5: Rapportering og metrics

Etter prosesseringssyklusen:

1. **Manglende kvitteringer:** Transaksjoner sendt for mer enn X timer siden uten kvittering rapporteres til Slack
2. **Prometheus-metrics:** Antall trekk per status oppdateres og eksponeres på `/internal/metrics`
3. **Datavask:** Trekk tilknyttet avsluttede saker eldre enn 6 måneder slettes

---

## Feilhåndtering

| Feilscenario | Håndtering |
|-------------|------------|
| SKE API utilgjengelig | Logger feil, stopper henting for denne syklusen |
| Valideringsfeil i trekk | Status settes til `AVVIST`, meldes til Slack |
| MQ-sending feiler | Transaksjonen forblir `IKKE_SENDT`, prøves på nytt neste syklus |
| Kvittering med feil | Status `FEIL`, feilkode lagres, Slack-varsel sendes |
| Manglende kvittering | Rapporteres til Slack etter timeout |
