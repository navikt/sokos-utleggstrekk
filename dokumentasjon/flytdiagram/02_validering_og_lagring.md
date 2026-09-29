# 2. Validering og lagring

Hvert trekkpålegg valideres og lagres i databasen. Ugyldige trekk markeres som AVVIST men lagres likevel for sporbarhet.

```mermaid
%%{init: {'theme': 'base', 'themeVariables': {'background': '#ffffff', 'primaryTextColor': '#000000', 'secondaryTextColor': '#000000', 'tertiaryTextColor': '#000000', 'lineColor': '#555555', 'textColor': '#000000'}, 'flowchart': {'wrappingWidth': 200, 'padding': 15, 'nodeSpacing': 30, 'rankSpacing': 40}}}%%
flowchart LR
    Input[Liste av Trekkpaalegg<br/>sortert etter sekvensnr] --> ForEach[For hvert<br/>trekkpålegg]

    ForEach --> Validate{Validering OK?}

    Validate -->|Ja| Insert[(INSERT fraskatt<br/>status: MOTTATT)]
    Validate -->|Nei| Avvist[(INSERT fraskatt<br/>status: AVVIST)]

    Avvist --> SlackErr[Slack-alarm:<br/>valideringsfeil]

    Insert --> Next[Neste trekkpålegg]
    SlackErr --> Next



    style Input fill:#e8d5f5,stroke:#c4a4d9,color:#000
    style ForEach fill:#ffe0f0,stroke:#e6a8c8,color:#000
    style Validate fill:#fff3b0,stroke:#e6d476,color:#000
    style Insert fill:#d4f0c4,stroke:#9dcc8a,color:#000
    style Avvist fill:#ffc4c4,stroke:#e69a9a,color:#000
    style SlackErr fill:#ffc4c4,stroke:#e69a9a,color:#000
    style Next fill:#d4f0c4,stroke:#9dcc8a,color:#000
```

## Nøkkelpunkter

- Trekkpålegg sorteres etter sekvensnummer slik at vi aldri lagrer et nyere trekk uten å ha lagret alle eldre
- Valideringsfeil gir status `AVVIST` men stopper ikke prosessering av neste trekk
- Databasefeil derimot kaster exception og stopper all videre henting (for å unngå hull i sekvensen)
- Hvert trekkpålegg lagres med sine perioder, betalingsinformasjon og status i egne tabeller

## Datamodell

| Tabell | Innhold |
|--------|---------|
| `fraskatt` | Hoveddata: trekkid, trekkversjon, skyldner, saksnummer, trekkstatus |
| `periode` | Perioder med start/slutt-dato og sats (prosent eller beløp) |
| `betalingsinformasjon` | Kreditors kontonr, orgnr, KID |
| `fraskatt_status` | Nåværende behandlingsstatus (MOTTATT, BEHANDLET, AVVIST, ...) |
