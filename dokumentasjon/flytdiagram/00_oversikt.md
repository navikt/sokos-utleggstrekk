# 0. Oversikt – Schedulert jobb

Hver time trigges `UtleggsTrekkService.schedule()`. Flyten styres av tre uavhengige feature toggles i Unleash.

```mermaid
%%{init: {'theme': 'base', 'themeVariables': {'background': '#ffffff', 'primaryTextColor': '#000000', 'secondaryTextColor': '#000000', 'tertiaryTextColor': '#000000', 'lineColor': '#555555', 'textColor': '#000000'}, 'flowchart': {'wrappingWidth': 200, 'padding': 15, 'nodeSpacing': 30, 'rankSpacing': 40}}}%%
flowchart LR
    Trigger([Scheduler<br/>hver time]) --> Hent[ Hent nye<br/>trekkpålegg fra SKE]

    Hent -->  Behandle[ Behandle trekk<br/>lag dokumenter]
    Behandle --> Send[ Send dokumenter<br/>til Oppdrag Z]


    style Trigger fill:#e8d5f5,stroke:#c4a4d9,color:#000
    style Hent fill:#b4d7ff,stroke:#7baed4,color:#000
    style Behandle fill:#ffe0f0,stroke:#e6a8c8,color:#000
    style Send fill:#ffe0f0,stroke:#e6a8c8,color:#000

```

## Daglig jobb (kl 08:00)

I tillegg kjøres `reportMissingKvittering()` daglig. Denne rapporterer transaksjoner sendt til OS for mer enn 24 timer siden som fortsatt mangler kvittering.

```mermaid
%%{init: {'theme': 'base', 'themeVariables': {'background': '#ffffff', 'primaryTextColor': '#000000', 'secondaryTextColor': '#000000', 'tertiaryTextColor': '#000000', 'lineColor': '#555555', 'textColor': '#000000'}, 'flowchart': {'wrappingWidth': 200, 'padding': 15, 'nodeSpacing': 30, 'rankSpacing': 40}}}%%
flowchart LR
    Trigger([Scheduler<br/>kl 08:00]) --> Query[(Hent transaksjoner<br/>uten kvittering)]
    Query --> Filter{Sendt for mer<br/>enn 24t siden?}
    Filter -->|Ja| Slack[Rapporter til Slack]
    Filter -->|Nei| Done([Ingen rapportering])

    style Trigger fill:#e8d5f5,stroke:#c4a4d9,color:#000
    style Query fill:#b4d7ff,stroke:#7baed4,color:#000
    style Filter fill:#fff3b0,stroke:#e6d476,color:#000
    style Slack fill:#ffc4c4,stroke:#e69a9a,color:#000
    style Done fill:#d4f0c4,stroke:#9dcc8a,color:#000
```

## Feature toggles (Unleash)

| Toggle | Styrer |
|--------|--------|
| `sokos-utleggstrekk.hent-fra-ske.enabled` | Henting av trekk fra Skatteetaten |
| `sokos-utleggstrekk.prosesser-utleggstrekk.enabled` | Behandling/prosessering av trekk |
| `sokos-utleggstrekk.send-til-os.enabled` | Sending til Oppdrag Z |
