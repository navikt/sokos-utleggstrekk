# 5. Kvittering fra Oppdrag Z

`JmsListenerService` lytter kontinuerlig på kvitteringskøen. Kvitteringer prosesseres asynkront uavhengig av den schedulerte jobben.

```mermaid
%%{init: {'theme': 'base', 'themeVariables': {'background': '#ffffff', 'primaryTextColor': '#000000', 'secondaryTextColor': '#000000', 'tertiaryTextColor': '#000000', 'lineColor': '#555555', 'textColor': '#000000'}, 'flowchart': {'wrappingWidth': 200, 'padding': 15, 'nodeSpacing': 30, 'rankSpacing': 40}}}%%
flowchart LR
    MQ([MQ kvitteringskø]) --> Listener[JmsListenerService<br/>onReceipt]
    Listener --> Parse[Parse JSON til<br/>KvitteringFraOppdrag]

    Parse --> Status{Alvorlighetsgrad?}

    Status -->|00| OK[(kvittering_status → OK<br/>lagre navTrekkId)]
    Status -->|04 / 08| Feil[(kvittering_status → FEIL<br/>lagre feilmelding)]
    Status -->|Annet| Ukjent[(kvittering_status → UKJENT)]

    Feil --> LogErr[Logg feilkode<br/>+ Slack-alarm]

    Parse -->|Parse-feil| BOQ[Send til<br/>backout-kø]

    style MQ fill:#e8d5f5,stroke:#c4a4d9,color:#000
    style Listener fill:#ffe0f0,stroke:#e6a8c8,color:#000
    style Parse fill:#ffe0f0,stroke:#e6a8c8,color:#000
    style Status fill:#fff3b0,stroke:#e6d476,color:#000
    style OK fill:#d4f0c4,stroke:#9dcc8a,color:#000
    style Feil fill:#ffc4c4,stroke:#e69a9a,color:#000
    style Ukjent fill:#ffd4b8,stroke:#e6a87a,color:#000
    style LogErr fill:#ffc4c4,stroke:#e69a9a,color:#000
    style BOQ fill:#ffc4c4,stroke:#e69a9a,color:#000
```

## Kvitteringsstatus

| Alvorlighetsgrad | Kvitteringsstatus | Betydning |
|---|---|---|
| 00 | `OK` | Trekket er akseptert av Oppdrag Z |
| 04 / 08 | `FEIL` | Trekket er avvist (feilmelding lagres) |
| Annet | `UKJENT` | Uventet alvorlighetsgrad |

## Feilhåndtering

- Kvitteringert som feiler under parsing, validering eller prosessering sendes til en **backout-kø** (BOQ) 
- `MessageFormatException` (tom/uleselig melding) sendes **ikke** til BOQ
- Ved `FEIL`-kvittering lagres feilkode og beskrivelse i `feilmelding`-tabellen
- Fødselsnummer i feilbeskrivelser maskeres i logger

## Nøkkelpunkter

- Lytteren kjører kontinuerlig (ikke bare under scheduler-kjøring)
- Meldinger `acknowledge()`-es etter vellykket prosessering
- `navTrekkId` fra kvitteringen lagres for å knytte trekket i OS tilbake til vår transaksjon
