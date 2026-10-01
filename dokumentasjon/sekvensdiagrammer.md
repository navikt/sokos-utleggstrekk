
# Sekvensdiagrammer

## Komplett jobb-sekvens (oversikt)

```mermaid
sequenceDiagram
    participant Scheduler
    participant Service as UtleggsTrekkService
    participant SKE as Skatteetaten
    participant DB
    participant Behandling as BehandleTrekkService
    participant MQ as IBM MQ
    participant OZ as Oppdrag Z
    participant Slack

    Scheduler->>Service: schedule()

    rect rgb(230, 245, 255)
    Note over Service,DB: 1. Henting fra Skatteetaten
    Service->>DB: getLastSekvensnummer()
    DB-->>Service: sekvensnr = N
    Service->>SKE: GET /trekkpaalegg?fraSekvensnummer=N&maksAntall=2500
    SKE-->>Service: Liste med trekkpålegg
    Service->>Service: Valider hvert trekk
    Service->>DB: insertTrekkFraSkatt() (for hvert trekk)
    end

    rect rgb(230, 255, 230)
    Note over Service,DB: 2. Behandling
    Service->>Behandling: behandleTrekk()
    Behandling->>DB: getTrekkIdTilTrekkSomSkalBehandles()
    DB-->>Behandling: Liste med fraskatt_id
    loop For hvert trekk
        Behandling->>DB: getTrekkFraSkatt() + getPerioderForTrekk()
        Behandling->>Behandling: Beregn diff + lag dokumenter
        Behandling->>DB: insertTransaksjonTilOs()
        Behandling->>DB: updateTrekkFraSkattStatus(BEHANDLET)
    end
    end

    rect rgb(255, 245, 230)
    Note over Service,MQ: 3. Sending til Oppdrag Z
    Service->>DB: getTransaksjonerTilOsSomIkkeErSendt()
    DB-->>Service: Transaksjoner med IKKE_SENDT
    loop For hver transaksjon
        Service->>Service: Valider dokument-JSON
        Service->>MQ: send(dokumentJson)
        MQ->>OZ: Melding på kø
        Service->>DB: updateTransaksjonSendt()
    end
    end

    rect rgb(255, 230, 230)
    Note over Service,DB: 4. Opprydding
    Service->>DB: deleteOldData() (trekk avsluttet > 6 mnd)
    end

    Service->>Slack: sendCachedErrors() (hvis feil oppstod)
```

---

## Henting av trekk fra Skatteetaten

```mermaid
sequenceDiagram
    participant Service as UtleggsTrekkService
    participant Maskinporten
    participant SKE as Skatteetaten
    participant DB

    Service->>DB: getLastSekvensnummer()
    DB-->>Service: sekvensnr = N

    Service->>Maskinporten: Hent/bruk cachet token
    Maskinporten-->>Service: Bearer token

    loop Gjenta så lenge antall == MAX_ANTALL (2500)
        Service->>SKE: GET /trekkpaalegg?fraSekvensnummer=N&maksAntall=2500<br/>
        SKE-->>Service: JSON-array med trekkpålegg

        loop For hvert trekk (sortert på sekvensnummer)
            Service->>Service: validate() - sjekk feltverdier
            alt Gyldig
                Service->>DB: INSERT fraskatt + periode + betalingsinfo + fraskatt_status(MOTTATT)
            else Ugyldig
                Service->>DB: INSERT fraskatt + fraskatt_status(AVVIST)
                Service->>Service: Legg til Slack-feil i cache
            end
        end
    end
```

---

## Behandling av trekk for å lage meldinger til Oppdrag Z

```mermaid
sequenceDiagram
    participant BTS as BehandleTrekkService
    participant DB
    participant Slack

    BTS->>DB: getTrekkIdTilTrekkSomSkalBehandles()<br/>(status MOTTATT eller REPETERES)
    DB-->>BTS: Liste med fraskatt_id-er

    loop For hvert trekk (i transaksjon)
        BTS->>DB: getTrekkFraSkatt(id)
        DB-->>BTS: TrekkFraSkatt

        BTS->>DB: getSkattTrekkStatus(id)
        DB-->>BTS: Status

        alt Status = REPETERES og nyere versjon finnes
            BTS->>DB: updateStatus(HOPPET_OVER)
        else Normal behandling
            BTS->>DB: getOsAlternativForTrekk() (LOPP/LOPM)
            BTS->>DB: getPerioderForTrekk()
            BTS->>DB: getPerioderTilOs() (tidligere sendte)
            BTS->>BTS: Beregn diff: nye perioder, nullinger
            BTS->>BTS: Lag TrekkTilOppdrag-dokumenter (1 eller 2)

            loop For hvert dokument
                BTS->>DB: insertTransaksjonTilOs(dto)
            end
            BTS->>DB: updateStatus(BEHANDLET)
        end

        alt Feil under behandling
            BTS->>DB: updateStatus(AVVIST)
        end
    end
```

---

## Sending av meldinger til Oppdrag Z

```mermaid
sequenceDiagram
    participant Service as UtleggsTrekkService
    participant DB
    participant Validator
    participant MQ as JmsProducerService
    participant OZ as Oppdrag Z
    participant Slack

    Service->>DB: getTransaksjonerTilOsSomIkkeErSendt()
    DB-->>Service: Liste med TransaksjonOS

    loop For hver transaksjon
        Service->>Validator: validate(dokumentJson)

        alt Validering OK
            Service->>MQ: send(dokumentJson)
            MQ->>OZ: TextMessage på sendekø
            MQ->>MQ: commit()
            Service->>DB: updateTransaksjonSendt(SENDT)
        else Valideringsfeil
            Service->>DB: updateTransaksjonValideringsfeil()
            Service->>Slack: addError(FEIL_VED_SENDING)
        else MQ-feil
            MQ->>MQ: rollback()
            Service->>Slack: addError(FEIL_VED_SENDING)
        end
    end
```

---

## Mottak av kvitteringer fra Oppdrag Z

```mermaid
sequenceDiagram
    participant OZ as Oppdrag Z
    participant MQ as Kvitteringskø
    participant Listener as JmsListenerService
    participant DB
    participant BOQ as Backout Queue
    participant Slack

    OZ->>MQ: Kvitteringsmelding
    MQ->>Listener: onReceipt(message)

    Listener->>Listener: Parse JSON til KvitteringFraOppdrag
    Listener->>Listener: validate()

    alt Parse/validering OK
        Listener->>Listener: Bestem KvitteringStatus fra alvorlighetsgrad
        alt Alvorlighetsgrad = "00" (OK)
            Listener->>DB: updateReceiptStatusOfTransaksjon(OK, navTrekkId)
        else Alvorlighetsgrad = "04"/"08" (FEIL)
            Listener->>DB: updateReceiptStatusOfTransaksjon(FEIL, navTrekkId)
            Listener->>DB: insertFeilmeldingFraOS(kvittering)
            Listener->>Slack: addError(KVITTERING_FEIL)
        else Annet
            Listener->>DB: updateReceiptStatusOfTransaksjon(UKJENT, navTrekkId)
        end
        Listener->>MQ: message.acknowledge()
    else Parse/validering feiler
        alt MessageFormatException
            Note over Listener,MQ: Ikke BOQ og ikke acknowledge
        else Annen parse/valideringsfeil
            Listener->>BOQ: send(original melding)
            Listener->>MQ: message.acknowledge()
        end
        Listener->>Slack: addError(PROCESSING_FEIL)
    end
```

---

## Daglig rapport: Manglende kvitteringer

```mermaid
sequenceDiagram
    participant Scheduler
    participant Service as UtleggsTrekkService
    participant DB
    participant Slack

    Note over Scheduler: Kjøres kl 08:00 daglig
    Scheduler->>Service: reportMissingKvittering()

    Service->>DB: getTransakjonerTilOsSomManglerKvittering()<br/>(SENDT + IKKE_MOTTATT)
    DB-->>Service: Transaksjoner uten kvittering

    loop For hver transaksjon sendt > 24 timer siden
        Service->>Slack: addError(MANGLENDE_KVITTERING, transaksjonsId)
    end

    Service->>Slack: sendCachedErrors(KVITTERING_UTEBLIR)
```

---

## Maskinporten token-flyt

```mermaid
sequenceDiagram
    participant App as SkeClient
    participant Cache as Token Cache
    participant MP as Maskinporten

    App->>Cache: getAccessToken()

    alt Token gyldig (> 1 min til utløp)
        Cache-->>App: Cachet token
    else Token utløpt/mangler
        Cache->>MP: GET /.well-known/openid-configuration
        MP-->>Cache: {token_endpoint, issuer}

        Cache->>Cache: Bygg JWT-assertion<br/>- iss: clientId<br/>- aud: issuer<br/>- scope: skatteetaten:trekkpaalegg<br/>- systemuser_org: 995277670
        Cache->>Cache: Signer med RSA-nøkkel

        Cache->>MP: POST token_endpoint<br/>grant_type=jwt-bearer&assertion=signed-jwt
        MP-->>Cache: {access_token, expires_in}

        Cache-->>App: Nytt token
    end
```
