package no.nav.sokos.utleggstrekk.client

import java.util.UUID

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import io.ktor.serialization.JsonConvertException
import mu.KotlinLogging

import no.nav.sokos.utleggstrekk.config.PropertiesConfig
import no.nav.sokos.utleggstrekk.domene.ske.Trekkpaalegg
import no.nav.sokos.utleggstrekk.security.maskinporten.MaskinportenAccessTokenClient

const val MAX_ANTALL = 2500
private const val KLIENT_ID = "NAV/0.1"

private val logger = KotlinLogging.logger { }

class SkeClient(
    private val client: HttpClient = httpClient,
    private val tokenProvider: MaskinportenAccessTokenClient = MaskinportenAccessTokenClient(PropertiesConfig.MaskinportenClientConfig(), client),
) {
    val basePath = PropertiesConfig.SKEConfig().skeRestUrl

    // TODO: skal ikke brukes
    suspend fun hentAlleUtleggstrekk(): List<Trekkpaalegg> =
        client
            .get {
                url(basePath)
                headers(commonHeaders())
            }.toTrekkpaalegg()

    suspend fun hentUtleggstrekkFraSekvensnr(sekvensnr: Int): List<Trekkpaalegg> =
        client
            .get {
                url("$basePath?fraSekvensnummer=$sekvensnr&maksAntall=$MAX_ANTALL")
                headers(commonHeaders())
            }.toTrekkpaalegg()

    private suspend fun commonHeaders(): HeadersBuilder.() -> Unit {
        val token = tokenProvider.getAccessToken()
        return {
            append("Klientid", KLIENT_ID)
            append("Korrelasjonsid", UUID.randomUUID().toString()) // TODO: Hvis dette skal være noe poeng må den tas vare på et sted!
            append(HttpHeaders.Authorization, "Bearer $token")
        }
    }

    private suspend fun HttpResponse.toTrekkpaalegg() =
        try {
            body<List<Trekkpaalegg>>()
        } catch (e: JsonConvertException) {
            logger.error { "Feil i konvertering av response: ${e.message}" }
            emptyList()
        }
}