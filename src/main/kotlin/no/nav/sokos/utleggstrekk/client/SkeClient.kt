package no.nav.sokos.utleggstrekk.client

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.InternalAPI
import no.nav.sokos.utleggstrekk.config.PropertiesConfig
import no.nav.sokos.utleggstrekk.security.maskinporten.MaskinportenAccessTokenClient
import java.util.UUID

private const val maxAntall = 2500
private const val KLIENT_ID = "NAV/0.1"

class SkeClient(
    private val client: HttpClient = httpClient,
    private val tokenProvider: MaskinportenAccessTokenClient = MaskinportenAccessTokenClient(PropertiesConfig.MaskinportenClientConfig(), httpClient),
) {
    val basePath = "${PropertiesConfig.SKEConfig().skeRestUrl}"

    suspend fun hentAlleUtleggstrekk() = doGet(basePath, UUID.randomUUID().toString())

    suspend fun hentUtleggstrekkFraSekvensnr(sekvensnr: Int) =
        doGet("${basePath}$sekvensnr/$maxAntall", UUID.randomUUID().toString())

    @OptIn(InternalAPI::class)
    private suspend fun doGet(path: String, corrID: String):HttpResponse {
        val response= client.get(buildHttpRequest(path, corrID))
        return response
    }

    private suspend fun buildHttpRequest(path: String, corrID: String): HttpRequestBuilder {
        val token = tokenProvider.hentAccessToken()
        return HttpRequestBuilder().apply {
            url(path)
            headers {
                append("Klientid", KLIENT_ID)
                append("Korrelasjonsid", corrID)
                append(HttpHeaders.Authorization, "Bearer $token")
            }
        }
    }
}