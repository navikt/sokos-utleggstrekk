# 3. Behandling av trekk

`BehandleTrekkService.behandleTrekk()` henter alle ubehandlede trekk og lager dokumenter (JSON) som skal sendes til Oppdrag Z.

```mermaid
%%{init: {'theme': 'base', 'themeVariables': {'background': '#ffffff', 'primaryTextColor': '#000000', 'secondaryTextColor': '#000000', 'tertiaryTextColor': '#000000', 'lineColor': '#555555', 'textColor': '#000000'}, 'flowchart': {'wrappingWidth': 200, 'padding': 15, 'nodeSpacing': 30, 'rankSpacing': 40}}}%%
flowchart LR
    Start[(Hent trekk med status<br/>MOTTATT / REPETERES)] --> ForEach[For hvert trekk]

    ForEach --> RepCheck{Status =<br/>REPETERES?}
    RepCheck -->|Ja| Newer{Nyere versjon<br/>finnes?}
    Newer -->|Ja| Skip[(Sett status<br/>HOPPET_OVER)]
    Newer -->|Nei| Process
    RepCheck -->|Nei| Process[Beregn perioder<br/>+ lag dokumenter]

    Process --> Split{Både prosent<br/>og beløp?}
    Split -->|Ja| TwoDocs[Lag 2 dokumenter<br/>LOPP + LOPM]
    Split -->|Nei| OneDoc[Lag 1 dokument]

    TwoDocs --> Save[(INSERT<br/>transaksjon_os)]
    OneDoc --> Save

    Save --> Done[(Sett status<br/>BEHANDLET)]

    Process -->|Feil| Avvist[(Sett status<br/>AVVIST)]

    style Start fill:#b4d7ff,stroke:#7baed4,color:#000
    style ForEach fill:#ffe0f0,stroke:#e6a8c8,color:#000
    style RepCheck fill:#fff3b0,stroke:#e6d476,color:#000
    style Newer fill:#fff3b0,stroke:#e6d476,color:#000
    style Skip fill:#ffd4b8,stroke:#e6a87a,color:#000
    style Process fill:#ffe0f0,stroke:#e6a8c8,color:#000
    style Split fill:#fff3b0,stroke:#e6d476,color:#000
    style TwoDocs fill:#e8d5f5,stroke:#c4a4d9,color:#000
    style OneDoc fill:#e8d5f5,stroke:#c4a4d9,color:#000
    style Save fill:#b4d7ff,stroke:#7baed4,color:#000
    style Done fill:#d4f0c4,stroke:#9dcc8a,color:#000
    style Avvist fill:#ffc4c4,stroke:#e69a9a,color:#000
```

## Aksjonskode

Aksjonskoden bestemmes per trekkalternativ:

```mermaid
%%{init: {'theme': 'base', 'themeVariables': {'background': '#ffffff', 'primaryTextColor': '#000000', 'secondaryTextColor': '#000000', 'tertiaryTextColor': '#000000', 'lineColor': '#555555', 'textColor': '#000000'}, 'flowchart': {'wrappingWidth': 200, 'padding': 15, 'nodeSpacing': 30, 'rankSpacing': 40}}}%%
flowchart LR
    Trekk[Trekk mottas] --> Check{Trekkalternativet<br/>allerede kjent i OS?}
    Check -->|Ja| ENDR[Aksjonskode = ENDR]
    Check -->|Nei| NY[Aksjonskode = NY]

    style Trekk fill:#e8d5f5,stroke:#c4a4d9,color:#000
    style Check fill:#fff3b0,stroke:#e6d476,color:#000
    style ENDR fill:#ffe0f0,stroke:#e6a8c8,color:#000
    style NY fill:#d4f0c4,stroke:#9dcc8a,color:#000
```

## Avsluttede trekk

Når et trekk har `trekkstatus = AVSLUTTET` sendes et dokument **uten perioder** der kun `gyldigTomDato` settes til dagens dato.

## Nøkkelpunkter

- Hele behandlingen skjer i en databasetransaksjon per trekk
- Ved feil rulles transaksjonen tilbake og trekket settes til AVVIST
- REPETERES-trekk sjekkes mot nyere versjoner for å unngå å overskrive nyere data
- Dokumentet som lages er en `TrekkTilOppdrag` JSON som serialiseres og lagres i `transaksjon_os`
