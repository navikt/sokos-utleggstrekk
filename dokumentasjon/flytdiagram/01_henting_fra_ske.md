# 1. Henting fra Skatteetaten

Henter nye trekkpålegg fra Skatteetatens REST API paginert fra siste kjente sekvensnummer. Autentiseres med Maskinporten-token via systembruker.

```mermaid
%%{init: {'theme': 'base', 'themeVariables': {'background': '#ffffff', 'primaryTextColor': '#000000', 'secondaryTextColor': '#000000', 'tertiaryTextColor': '#000000', 'lineColor': '#555555', 'textColor': '#000000'}, 'flowchart': {'wrappingWidth': 200, 'padding': 15, 'nodeSpacing': 30, 'rankSpacing': 40}}}%%
flowchart LR
     SekvNr[(Hent siste<br/>sekvensnummer fra DB)] --> API[GET /trekkpaalegg<br/>fraSekvensnummer & maksAntall]

    API --> Response{HTTP-respons?} --> Parse[Parse JSON til<br/>liste av Trekkpaalegg]

    Parse --> Store[Lagre i DB<br/>sortert etter sekvensnr]


    style SekvNr fill:#b4d7ff,stroke:#7baed4,color:#000
    style API fill:#b4d7ff,stroke:#7baed4,color:#000
    style Response fill:#fff3b0,stroke:#e6d476,color:#000
    style Parse fill:#ffe0f0,stroke:#e6a8c8,color:#000
    style Store fill:#d4f0c4,stroke:#9dcc8a,color:#000
```

## Paginering

Henting skjer i en `do-while`-loop. Hver side henter opptil 2500 trekkpålegg (`MAX_ANTALL`). Dersom svaret inneholder nøyaktig 2500, finnes det sannsynligvis flere og neste side hentes med oppdatert sekvensnummer.

## Nøkkelpunkter

- Trekkpålegg lagres sortert etter sekvensnummer for å unngå hull ved feil
- Maskinporten-token hentes via systembruker (NAV Økonomilinjen, org.nr 995277670)
- Ved parse- eller lagringsfeil avbrytes videre henting
