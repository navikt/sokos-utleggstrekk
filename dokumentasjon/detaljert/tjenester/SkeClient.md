# SkeClient

**Pakke:** `no.nav.sokos.utleggstrekk.client`  
**Fil:** `client/SkeClient.kt`

## Ansvar

REST-klient mot Skatteetatens API for trekkpålegg. Håndterer autentisering via Maskinporten, feilhåndtering og deserialisering av responser.

## Avhengigheter

| Avhengighet                     | Rolle                |
|---------------------------------|----------------------|
| `HttpClient` (Ktor)             | Utfører HTTP-kall    |
| `MaskinportenAccessTokenClient` | Leverer Bearer-token |
| `SlackService`                  | Varsler om API-feil  |

## Konfigurasjon

| Konstant     | Verdi                                       | Beskrivelse                               |
|--------------|---------------------------------------------|-------------------------------------------|
| `MAX_ANTALL` | 2500                                        | Maks antall trekk per API-kall            |
| `KLIENT_ID`  | `"NAV/0.1"`                                 | Identifikator sendt i `Klientid`-headeren |
| `basePath`   | Fra `PropertiesConfig.skeConfig.skeRestUrl` | Basis-URL til SKE API                     |

---

## Funksjoner

### `suspend fun hentUtleggstrekkFraSekvensnr(sekvensnr: Int): List<Trekkpaalegg>`

**Synlighet:** public  
Henter alle trekkpålegg fra og med et gitt sekvensnummer.

**Steg:**
1. Genererer en unik `korrId` (UUID) for sporbarhet
2. Kaller `GET <basePath>?fraSekvensnummer=<sekvensnr>&maksAntall=2500`
3. Setter obligatoriske headere: `Klientid`, `Korrelasjonsid`, `Authorization: Bearer <token>`
4. Delegerer feilhåndtering til `handleError()` og deserialisering til `toTrekkpaalegg()`

**Returnerer:** Liste med `Trekkpaalegg`, eller tom liste ved feil.

---

### `private suspend fun commonHeaders(korrId: String): HeadersBuilder.() -> Unit`

Bygger HTTP-headere for API-kallet:
- Henter et gyldig Bearer-token fra `MaskinportenAccessTokenClient`
- Setter `Klientid`, `Korrelasjonsid` og `Authorization`

---

### `private suspend fun HttpResponse.handleError(sekvensnr: Int): HttpResponse?`

Utvidelsefunksjon på `HttpResponse`. Håndterer ikke-vellykkede HTTP-responser:

| HTTP-statuskode | Håndtering                                                        |
|-----------------|-------------------------------------------------------------------|
| 2xx             | Returnerer response-objektet videre                               |
| 4xx / 5xx       | Prøver å deserialisere `SkeErrorMessage` og legger til Slack-feil |
| Andre feil      | Logger headers og statuskode                                      |

**Returnerer:** `HttpResponse` ved suksess, `null` ved feil.

---

### `private suspend fun HttpResponse.toTrekkpaalegg(sekvensnr, korrId): List<Trekkpaalegg>`

Utvidelsefunksjon på `HttpResponse`. Deserialiserer responsbody til en liste med `Trekkpaalegg`.

**Feilhåndtering:**

| Feiltype                                            | Håndtering                                     |
|-----------------------------------------------------|------------------------------------------------|
| `JsonConvertException` / `IllegalArgumentException` | Logger konverteringsfeil, returnerer tom liste |
| `IllegalStateException` (tom body)                  | Logger advarsel, returnerer tom liste          |

Validerer strengen før deserialisering for å fange opp ugyldige tegn.
