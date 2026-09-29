# Arkitektur

## Systemkontekst

sokos-utleggstrekk er en backend-tjeneste uten brukergrensesnitt som fungerer som en bro mellom Skatteetatens trekkpålegg-API og NAVs Oppdragssystem (Oppdrag Z).

```mermaid
C4Context
    title Systemkontekst - sokos-utleggstrekk

    Person(drift, "Driftspersonell", "Overvåker via Slack og Grafana")

    System(app, "sokos-utleggstrekk", "Kotlin/Ktor<br/>Henter utleggstrekk fra SKE<br/>og sender til Oppdrag Z")

    System_Ext(ske, "Skatteetaten", "REST API for trekkpålegg")
    System_Ext(oz, "Oppdrag Z", "Stormaskin<br/>IBM MQ")
    System_Ext(maskinporten, "Maskinporten", "OAuth2 tokentjeneste")

    SystemDb(db, "PostgreSQL", "Cloud SQL i GCP")
    System_Ext(slack, "Slack", "Alarmer og feilmeldinger")
    System_Ext(unleash, "Unleash", "Feature toggles")

    Rel(app, ske, "Henter trekkpålegg", "REST/HTTPS")
    Rel(app, maskinporten, "Henter access token", "OAuth2 JWT Bearer")
    Rel(app, oz, "Sender innrapporteringstrekk", "IBM MQ")
    Rel(oz, app, "Kvitteringer", "IBM MQ")
    Rel(app, db, "Lese/skrive", "JDBC/PostgreSQL")
    Rel(app, slack, "Alarmer", "Webhook")
    Rel(app, unleash, "Feature toggles", "HTTP")
    Rel(drift, slack, "Mottar alarmer")
```

## Deployment-modell

```mermaid
flowchart TB
    subgraph GCP["Google Cloud Platform (GCP)"]
        subgraph NAIS["NAIS-kluster"]
            APP[sokos-utleggstrekk<br/>Ktor/Netty<br/>Port 8080]
        end
        subgraph CloudSQL["Cloud SQL"]
            DB[(PostgreSQL 18)]
        end
    end

    subgraph OnPrem["On-Premise"]
        MQ[IBM MQ<br/>mqls02]
        OZ[Oppdrag Z<br/>Stormaskin]
    end

    subgraph Ekstern["Eksterne tjenester"]
        SKE[Skatteetaten API<br/>api-test.sits.no / api.sits.no]
        MP[Maskinporten<br/>ver2.maskinporten.no]
    end

    APP --> DB
    APP -->|webproxy| MQ
    MQ <--> OZ
    APP -->|HTTPS| SKE
    APP -->|OAuth2| MP
```

### Miljøer

| Miljø | Kluster | Ingress | MQ Host |
|-------|---------|---------|---------|
| Dev | dev-gcp | sokos-utleggstrekk.intern.dev.nav.no | mqls02.preprod.local:1413 |
| Prod | prod-gcp | sokos-utleggstrekk.intern.nav.no | mqls02.prod.local:1413 |

### Infrastrukturkomponenter

| Komponent | Teknologi | Formål |
|-----------|-----------|--------|
| Runtime | JVM 25 / Kotlin 2.4 | Applikasjonskode |
| Web-server | Ktor 3.5 / Netty | HTTP-endepunkter |
| Database | PostgreSQL 18 (Cloud SQL) | Persistering av trekk |
| Meldingskø | IBM MQ | Kommunikasjon med Oppdrag Z |
| Autentisering | Maskinporten + Systembruker | Tilgang til Skatteetatens API |
| Feature toggles | Unleash | Kill-switcher for hvert steg |
| Observability | Prometheus + Micrometer | Metrikker |
| Logging | Logback + Logstash | Strukturert logging |
| Alarmer | Slack Webhook | Feilvarsling |
| CI/CD | GitHub Actions | Bygg og deploy |

---

## Dataflyt (detaljert)

```mermaid
flowchart TD
    subgraph Henting["1. Henting fra Skatteetaten"]
        A1[Hent siste sekvensnummer fra DB] --> A2[Kall Skatteetatens API]
        A2 --> A3{Respons OK?}
        A3 -->|Ja| A4[Valider hvert trekk]
        A3 -->|Nei| A5[Logg feil + Slack-alarm]
        A4 --> A6{Gyldig?}
        A6 -->|Ja| A7[Lagre i fraskatt med status MOTTATT]
        A6 -->|Nei| A8[Lagre med status AVVIST]
    end

    subgraph Behandling["2. Behandling"]
        B1[Hent ubehandlede trekk] --> B2[For hvert trekk:]
        B2 --> B3["Finn kjente alternativ i OS<br/>(se periodeberegning)"]
        B3 --> B4["Beregn nye perioder<br/>(se periodeberegning)"]
        B4 --> B5[Generer InnrapporteringTrekk-dokumenter]
        B5 --> B6[Lagre i transaksjon_os med IKKE_SENDT]
        B6 --> B7[Sett fraskatt_status = BEHANDLET]
    end

    subgraph Sending["3. Sending til Oppdrag Z"]
        C1[Hent IKKE_SENDT transaksjoner] --> C2[Valider dokument-JSON]
        C2 --> C3[Send på MQ-kø]
        C3 --> C4[Sett transaksjon_status = SENDT]
    end

    subgraph Kvittering["4. Kvittering fra Oppdrag Z"]
        D1[MQ-listener mottar melding] --> D2[Parse kvittering]
        D2 --> D3{Alvorlighetsgrad?}
        D3 -->|00 OK| D4[Lagre nav_trekk_id]
        D3 -->|04/08 FEIL| D5[Lagre feilmelding + Slack-alarm]
    end

    Henting --> Behandling --> Sending
    Kvittering -.->|Asynkront| Sending
```

---

## Sikkerhet

### Autentiseringsflyt mot Skatteetaten

```mermaid
sequenceDiagram
    participant App as sokos-utleggstrekk
    participant MP as Maskinporten
    participant SKE as Skatteetaten

    App->>MP: Hent OpenID-konfigurasjon (well-known)
    MP-->>App: token_endpoint, issuer
    App->>App: Lag JWT-assertion med RSA-nøkkel<br/>(inkl. systembruker-claim for org 995277670)
    App->>MP: POST token_endpoint (grant_type=jwt-bearer)
    MP-->>App: access_token (Bearer)
    App->>SKE: GET /trekkpaalegg?fraSekvensnummer=N<br/>Authorization: Bearer token
    SKE-->>App: Liste med trekkpålegg (JSON)
```

### Sikkerhetsprinsipper

- **Maskinporten med systembruker**: Applikasjonen autentiserer seg med NAV Økonomilinjas organisasjonsnummer
- **RSA-signert JWT**: Private key fra NAIS-secret brukes til å signere JWT-forespørsler
- **Token-caching**: Access tokens caches og fornyes 1 minutt før utløp
- **Input-validering**: All data fra Skatteetaten valideres mot strenge regler (gyldige tegn, datoformat, lengder)
- **Output-validering**: Dokumenter valideres før de sendes til Oppdrag Z

---

## Skaleringsmodell

- **Replicas**: Alltid 1 instans (max=1, min=1) – jobben er ikke designet for parallellkjøring
- **Ressurser**: 100m CPU request, 512Mi–4096Mi minne
- **Database**: Cloud SQL med Point-in-Time Recovery og SSD
- **Auto-reconnect**: MQ-klienten har auto-reconnect til queue manager
