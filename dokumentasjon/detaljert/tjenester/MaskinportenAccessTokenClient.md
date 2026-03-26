# MaskinportenAccessTokenClient

**Pakke:** `no.nav.sokos.utleggstrekk.security.maskinporten`  
**Fil:** `security/maskinporten/MaskinportenAccessTokenClient.kt`

## Ansvar

Henter og cacher OAuth 2.0 Bearer-tokens fra Maskinporten. Brukes av `SkeClient` for å autentisere kall mot Skatteetatens API. Implementerer system-til-system autentisering med systembruker via JWT-grant.

## Avhengigheter

| Avhengighet                | Rolle                                         |
|----------------------------|-----------------------------------------------|
| `MaskinportenClientConfig` | Klient-ID, scopes, RSA-nøkkel, well-known URL |
| `HttpClient` (Ktor)        | Utfører HTTP-kall mot Maskinporten            |

---

## Token-caching

Tokenet caches i `cachedToken` og fornyes automatisk hvis det utløper **innen 1 minutt**. Coroutine-Mutex brukes for å hindre at flere coroutines henter token samtidig (race condition).

```
cachedToken != null OG utløper om > 1 min → bruk cachedToken
cachedToken == null ELLER utløper snart   → hent nytt token
```

---

## Funksjoner

### `suspend fun getAccessToken(): String`

**Synlighet:** public  
Returnerer et gyldig Bearer-token. Henter nytt fra Maskinporten hvis cache er tom eller tokenet nærmer seg utløp.

Trådsikkert via `Mutex.withLock {}`.

**Kaster:** `MaskinportenException` hvis henting feiler.

---

### `private suspend fun getMaskinportenToken(): AccessToken`

Gjennomfører tokenutveksling mot Maskinporten:

1. Henter OpenID-konfigurasjon (issuer, token endpoint) via `getOpenIdConfiguration()`
2. Bygger systembruker-claim for NAV Økonomilinjen
3. Lager signert JWT-assertion via `createJwtAssertion()`
4. Sender `POST` til token endpoint med `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer`
5. Returnerer `AccessToken` med token-streng og utløpstidspunkt

**Kaster:** `MaskinportenException` ved HTTP-feil fra Maskinporten.

---

### `private suspend fun getOpenIdConfiguration(): OpenIdConfiguration`

Henter OpenID Connect-konfigurasjon fra Maskinportens well-known URL.

**Kaster:** Exception (videre) ved nettverksfeil.

---

### `private fun createJwtAssertion(audience, additionalClaims): String`

Bygger og signerer en JWT-assertion for Maskinporten-autentisering.

---

### `private fun getSystembrukerClaim(orgNr: String): Map<String, Any>`

Bygger Altinn-systembruker-claim som inkluderes i JWT.
Dette claimet forteller Maskinporten at tokenet skal utstedes på vegne av NAV Økonomilinjen (995277670), ikke NAVs hoved-organisasjon.

---

## Interne dataklasser

| Klasse                      | Beskrivelse                                    |
|-----------------------------|------------------------------------------------|
| `AccessToken`               | Token-streng + utløpstidspunkt (`Instant`)     |
| `MaskinportenTokenResponse` | Deserialiseringsmodell for token-responsen     |
| `TokenError`                | Deserialiseringsmodell for feilresponser       |
| `OpenIdConfiguration`       | Deserialiseringsmodell for well-known metadata |
| `MaskinportenException`     | Kastes ved autentiseringsfeil                  |
