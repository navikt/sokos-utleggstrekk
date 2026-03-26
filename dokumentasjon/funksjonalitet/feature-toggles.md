# Feature toggles

## Oversikt

sokos-utleggstrekk bruker [Unleash](https://okonomi-unleash-web.iap.nav.cloud.nais.io/) for å styre hvilke deler av prosesseringsflyten som er aktive. Dette gjør det mulig å skru av/på funksjonalitet uten å deploye ny kode.

## Tilgjengelige toggles

| Toggle | Styrer |
|--------|--------|
| `sokos-utleggstrekk.hent-fra-ske.enabled` | Henting av trekkpålegg fra Skatteetaten |
| `sokos-utleggstrekk.prosesser-utleggstrekk.enabled` | Behandling og transformasjon av trekk |
| `sokos-utleggstrekk.send-til-os.enabled` | Sending av innrapporteringstrekk til Oppdrag Z |

## Konfigurasjon

| Miljøvariabel | Beskrivelse |
|---------------|-------------|
| `UNLEASH_SERVER_API_URL` | URL til Unleash API |
| `UNLEASH_SERVER_API_TOKEN` | API-nøkkel for autentisering |
| `UNLEASH_SERVER_API_ENV` | Miljønavn (`development` / `production`) |

Standardverdien (`enabledByDefault`) er `true` – alle steg kjører med mindre togglen er eksplisitt slått av.

## Bruksscenarier

### Stoppe henting uten å stoppe resten

Nyttig dersom SKE API er ustabilt eller under vedlikehold. Trekk som allerede er lagret i `MOTTATT`-status kan fortsatt behandles og sendes.

```
hent-fra-ske.enabled          = false   ← stopper ny henting
prosesser-utleggstrekk.enabled = true
send-til-os.enabled            = true
```

### Kun hente og lagre – ikke sende

Nyttig for å akkumulere data mens Oppdrag Z er utilgjengelig:

```
hent-fra-ske.enabled          = true
prosesser-utleggstrekk.enabled = true
send-til-os.enabled            = false  ← ingenting sendes til OS
```

### Full stopp

Slå av all prosessering uten å stoppe selve applikasjonen:

```
hent-fra-ske.enabled          = false
prosesser-utleggstrekk.enabled = false
send-til-os.enabled            = false
```
