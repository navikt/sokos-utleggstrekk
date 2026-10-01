# 4. Sending til Oppdrag Z

Dokumenter med status `IKKE_SENDT` hentes fra `transaksjon_os` og sendes til Oppdrag Z over IBM MQ.

```mermaid
%%{init: {'theme': 'base', 'themeVariables': {'background': '#ffffff', 'primaryTextColor': '#000000', 'secondaryTextColor': '#000000', 'tertiaryTextColor': '#000000', 'lineColor': '#555555', 'textColor': '#000000'}, 'flowchart': {'wrappingWidth': 200, 'padding': 15, 'nodeSpacing': 30, 'rankSpacing': 40}}}%%
flowchart LR
    Start[(Hent transaksjoner<br/>status: IKKE_SENDT)] --> ForEach[For hver<br/>transaksjon]

    ForEach --> Validate{Output-<br/>validering OK?}

    Validate -->|Ja| MQ[Send JSON<br/>til MQ-kø]
    Validate -->|Nei| ValFeil[(Status →<br/>VALIDERINGSFEIL)]

    MQ --> Commit{MQ commit OK?}

    Commit -->|Ja| Sendt[(Status → SENDT<br/>tidspunkt_sendt = nå)]
    Commit -->|Nei| Rollback[MQ rollback<br/>kast JMSException]

    Rollback --> Slack[Slack-alarm:<br/>feil ved sending]

    ValFeil --> Slack

    style Start fill:#b4d7ff,stroke:#7baed4,color:#000
    style ForEach fill:#ffe0f0,stroke:#e6a8c8,color:#000
    style Validate fill:#fff3b0,stroke:#e6d476,color:#000
    style MQ fill:#e8d5f5,stroke:#c4a4d9,color:#000
    style ValFeil fill:#ffc4c4,stroke:#e69a9a,color:#000
    style Commit fill:#fff3b0,stroke:#e6d476,color:#000
    style Sendt fill:#d4f0c4,stroke:#9dcc8a,color:#000
    style Rollback fill:#ffc4c4,stroke:#e69a9a,color:#000
    style Slack fill:#ffc4c4,stroke:#e69a9a,color:#000
```

## MQ-oppsett

| Kø | Retning | Formål |
|----|---------|--------|
| Sendekø | App → OS | Trekk-dokumenter (JSON) |
| Kvitteringskø | OS → App | Kvitteringer med alvorlighetsgrad |

Sendekøen har en `replyTo`-header som peker til kvitteringskøen, slik at Oppdrag Z vet hvor kvitteringen skal sendes.

## Nøkkelpunkter

- JSON-dokumentet valideres med `TrekkTilOppdrag.validate()` **før** sending
- MQ-sending er transaksjonell (`SESSION_TRANSACTED`): `commit()` ved suksess, `rollback()` ved feil
- Ved feilet commit kastes `JMSException` og meldingen sendes ikke
- Hvert dokument sendes enkeltvis (ikke batch)
