# 6. Opprydding

Kjøres ved slutten av hver schedulert jobb (`schedule()`). Sletter data for trekk som er avsluttet og eldre enn 6 måneder.

```mermaid
%%{init: {'theme': 'base', 'themeVariables': {'background': '#ffffff', 'primaryTextColor': '#000000', 'secondaryTextColor': '#000000', 'tertiaryTextColor': '#000000', 'lineColor': '#555555', 'textColor': '#000000'}, 'flowchart': {'wrappingWidth': 200, 'padding': 15, 'nodeSpacing': 30, 'rankSpacing': 40}}}%%
flowchart LR
    Start([deleteOldData]) --> Find[(Finn trekk hvor<br/>trekkstatus = AVSLUTTET<br/>OG opprettet > 6 mnd siden)]
    Find --> HasData{Finnes<br/>gamle trekk?}

    HasData -->|Nei| Done([Ingen opprydding])
    HasData -->|Ja| DelOS[(Slett relaterte rader<br/>i transaksjon_os)]

    DelOS --> DelFraskatt[(Slett rader i fraskatt<br/>cascade: periode,<br/>betalingsinfo, status)]
    DelFraskatt --> Done2([Opprydding ferdig])

    style Start fill:#e8d5f5,stroke:#c4a4d9,color:#000
    style Find fill:#b4d7ff,stroke:#7baed4,color:#000
    style HasData fill:#fff3b0,stroke:#e6d476,color:#000
    style Done fill:#d4f0c4,stroke:#9dcc8a,color:#000
    style DelOS fill:#ffc4c4,stroke:#e69a9a,color:#000
    style DelFraskatt fill:#ffc4c4,stroke:#e69a9a,color:#000
    style Done2 fill:#d4f0c4,stroke:#9dcc8a,color:#000
```

## Sletterekkefølge

Tabellene har fremmednøkler, så sletting skjer i riktig rekkefølge:

| Steg | Tabell | Relasjon |
|------|--------|----------|
| 1 | `transaksjon_os` (+ `periode_til_os`) | Knyttet via trekkid |
| 2 | `fraskatt` | Cascade sletter: `periode`, `betalingsinformasjon`, `fraskatt_status` |

## Nøkkelpunkter

- Når minst én avsluttet versjon er eldre enn 6 måneder, slettes alle versjoner og OS-transaksjoner med samme `trekkid`
- 6-måneders grensen gir tid til manuell feiloppfølging
- Kjøres alltid, uavhengig av feature toggles
