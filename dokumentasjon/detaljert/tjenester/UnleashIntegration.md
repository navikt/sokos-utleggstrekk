# UnleashIntegration

**Pakke:** `no.nav.sokos.utleggstrekk.unleash`  
**Fil:** `unleash/UnleashIntegration.kt`

## Ansvar

Integrerer mot Unleash for feature toggles. Lar driftsteamet skru av og på deler av prosesseringsflyten uten å deploye ny kode. Logger til info hver gang en toggle skifter tilstand.

I lokalt miljø og testemiljø brukes `FakeUnleash` som alltid returnerer standardverdien (`enabledByDefault`).

---

## Feature toggles

| Toggle-nøkkel                                       | Metode                             | Styrer                                         |
|-----------------------------------------------------|------------------------------------|------------------------------------------------|
| `sokos-utleggstrekk.hent-fra-ske.enabled`           | `isHentFraSKEEnabled()`            | Henting av trekkpålegg fra Skatteetaten        |
| `sokos-utleggstrekk.send-til-os.enabled`            | `isSendTilOSEnabled()`             | Sending av innrapporteringstrekk til Oppdrag Z |
| `sokos-utleggstrekk.prosesser-utleggstrekk.enabled` | `isProsesserUtleggstrekkEnabled()` | Behandling og transformasjon av trekkpålegg    |

---

## Funksjoner

### `fun isHentFraSKEEnabled(): Boolean`

Returnerer om henting fra Skatteetaten er aktivt.

---

### `fun isSendTilOSEnabled(): Boolean`

Returnerer om sending til Oppdrag Z er aktivt.

---

### `fun isProsesserUtleggstrekkEnabled(): Boolean`

Returnerer om behandling/transformasjon av trekkpålegg er aktivt.

---

### `fun isEnabled(toggleName: String): Boolean`

**Felles implementasjon** for alle toggle-sjekker:
1. Henter gjeldende tilstand fra Unleash-klienten (med `enabledByDefault` som fallback)
2. Sammenligner med sist kjente tilstand
3. Logger en `INFO`-melding hvis tilstanden har endret seg
4. Oppdaterer `lastStates`-mappet
5. Returnerer gjeldende tilstand

---

## Initialisering (`init`-blokk)

| Miljø        | Unleash-klient                                    |
|--------------|---------------------------------------------------|
| Lokal / Test | `FakeUnleash` (alle toggles = `enabledByDefault`) |
| Dev / Prod   | `DefaultUnleash` med synkron initialhenting       |

`DefaultUnleash` konfigureres med:
- `appName` – applikasjonsnavn
- `instanceId` – NAIS pod-navn
- `unleashAPI` – API-URL fra konfigurasjon
- `apiKey` – autentiseringsnøkkel
- `environment` – `development` eller `production`
- `synchronousFetchOnInitialisation = true` – henter toggles ved oppstart før første kjøring
