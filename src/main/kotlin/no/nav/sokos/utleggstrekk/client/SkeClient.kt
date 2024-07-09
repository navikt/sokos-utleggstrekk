package no.nav.sokos.utleggstrekk.client

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import no.nav.sokos.utleggstrekk.httpClient
import no.nav.sokos.utleggstrekk.security.maskinporten.MaskinportenAccessTokenClient
import java.util.UUID

private const val ORGNR = "889640782"
private const val KLIENT_ID = "NAV/0.1"

class SkeClient(
    private val tokenProvider: MaskinportenAccessTokenClient,
    private val skeEndpoint: String,
    private val client: HttpClient = httpClient,
) {

    suspend fun hentAlleNyeUtleggstrekk() = doGet(ORGNR, UUID.randomUUID().toString())

    private suspend fun doGet(path: String, corrID: String) = client.get(buildHttpRequest(path, corrID))

    private suspend fun buildHttpRequest(path: String, corrID: String): HttpRequestBuilder {
        val token = tokenProvider.hentAccessToken()
        return HttpRequestBuilder().apply {
            url("$skeEndpoint$path")
            headers {
                append("Klientid", KLIENT_ID)
                append("Korrelasjonsid", corrID)
                append(HttpHeaders.Authorization, "Bearer $token")
            }
        }
    }
}
