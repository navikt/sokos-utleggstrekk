# SlackService

**Pakke:** `no.nav.sokos.utleggstrekk.service`  
**Fil:** `service/SlackService.kt`

## Ansvar

Samler feilmeldinger lokalt under prosesseringen og sender dem samlet til Slack via webhook. Forhindrer at mange like feil genererer like mange separate Slack-meldinger (spam-beskyttelse).

Klassen er et singleton tilgjengelig via `SlackService.instance`.

## Avhengigheter

| Avhengighet   | Rolle                                       |
|---------------|---------------------------------------------|
| `SlackClient` | Utfører selve HTTP-kallet mot Slack-webhook |

---

## Datafelt

### `val errorTracking: MutableList<ErrorMessage>`

En lokal buffer av feilmeldinger gruppert etter type (`header`). Hver `ErrorMessage` inneholder:
- `type: String` – kort beskrivelse av feiltypen (brukes som grupperingsnøkkel)
- `info: MutableList<String>` – liste med detaljerte feilmeldinger av denne typen

---

## Funksjoner

### `fun addError(header: String, message: String)`

**Synlighet:** public  
Legger til en feilmelding i den lokale bufferen.

- Hvis det allerede finnes en gruppe med samme `header`, legges `message` til i den eksisterende listen
- Ellers opprettes en ny gruppe

**Parametere:**
| Parameter | Beskrivelse |
|-----------|-------------|
| `header` | Kort navn på feiltypen, f.eks. `"Feil fra SKE"` |
| `message` | Detaljert beskrivelse av den konkrete feilen |

---

### `suspend fun sendCachedErrors(messageTitle: String)`

**Synlighet:** public  
Sender alle bufrede feilmeldinger til Slack og tømmer bufferen.

**Spam-beskyttelse:** Hvis én feiltype har mer enn 5 meldinger, erstattes alle med ett sammendrag: `"N av samme type feil: <type>. Sjekk avstemming"`.

Gjør ingenting og returnerer tidlig hvis bufferen er tom.

**Parametere:**
| Parameter | Beskrivelse |
|-----------|-------------|
| `messageTitle` | Overskrift for Slack-meldingen, f.eks. `"Trekk henting alert"` |

---

## Bruksmønster

```kotlin
// Under prosessering – legg til feil i buffer
slackService.addError("Feil fra SKE", "Kunne ikke hente trekk for sekvensnr=42")
slackService.addError("Feil fra SKE", "Kunne ikke hente trekk for sekvensnr=43")

// Etter prosessering – send alt samlet
slackService.sendCachedErrors("Trekk henting alert")
// → Én Slack-melding med begge feilene
```
