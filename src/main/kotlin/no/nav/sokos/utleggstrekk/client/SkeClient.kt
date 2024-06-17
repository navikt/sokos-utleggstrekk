package no.nav.sokos.utleggstrekk.client

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import no.nav.sokos.utleggstrekk.httpClient
import no.nav.sokos.utleggstrekk.security.maskinporten.MaskinportenAccessTokenClient
import java.util.*

private const val ORGNR = "889640782"
private const val KLIENT_ID = "NAV/0.1"

class SkeClient(
    private val tokenProvider: MaskinportenAccessTokenClient,
    private val skeEndpoint: String,
    private val client: HttpClient = httpClient,
) {

    suspend fun hentAlleNyeUtleggstrekk() =
        doGet(ORGNR, UUID.randomUUID().toString())

    private suspend fun doGet(path: String, corrID: String):HttpResponse {
        val resp = client.get(buildHttpRequest(path, corrID))
        println(resp.bodyAsText())
        return resp
    }

    private suspend fun buildHttpRequest(path: String, corrID: String): HttpRequestBuilder {
        println("Henter Token")
        val token = tokenProvider.hentAccessToken()
        println("Token:  \n --$token")
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
