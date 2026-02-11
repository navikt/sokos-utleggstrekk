package no.nav.sokos.utleggstrekk.client

import java.util.UUID

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import io.ktor.serialization.JsonConvertException
import mu.KotlinLogging

import no.nav.sokos.utleggstrekk.config.PropertiesConfig
import no.nav.sokos.utleggstrekk.config.jsonConfig
import no.nav.sokos.utleggstrekk.domene.ske.SkeErrorMessage
import no.nav.sokos.utleggstrekk.domene.ske.Trekkpaalegg
import no.nav.sokos.utleggstrekk.security.maskinporten.MaskinportenAccessTokenClient
import no.nav.sokos.utleggstrekk.service.SlackService
import no.nav.sokos.utleggstrekk.utils.Validation.validateString
import no.nav.sokos.utleggstrekk.utils.isClientError
import no.nav.sokos.utleggstrekk.utils.isServerError
import no.nav.sokos.utleggstrekk.utils.isSuccessful

const val MAX_ANTALL = 2500
private const val KLIENT_ID = "NAV/0.1"

private val logger = KotlinLogging.logger { }

class SkeClient(
    private val client: HttpClient = httpClient,
    private val slackService: SlackService = SlackService.instance,
    private val tokenProvider: MaskinportenAccessTokenClient = MaskinportenAccessTokenClient(PropertiesConfig.maskinportenClientConfig, client),
) {
    val basePath = PropertiesConfig.skeConfig.skeRestUrl

    suspend fun hentUtleggstrekkFraSekvensnr(sekvensnr: Int): List<Trekkpaalegg> {
        val korrId = UUID.randomUUID().toString()
        logger.info { "Henter utleggstrekk fra sekvensnummer $sekvensnr, antall $MAX_ANTALL, korrId=$korrId" }
        return client
            .get {
                url("$basePath?fraSekvensnummer=$sekvensnr&maksAntall=$MAX_ANTALL")
                headers(commonHeaders(korrId))
            }.handleError(sekvensnr, korrId)
            ?.toTrekkpaalegg(sekvensnr, korrId) ?: emptyList()
    }

    private suspend fun commonHeaders(korrId: String): HeadersBuilder.() -> Unit {
        val token = tokenProvider.getAccessToken()
        return {
            append("Klientid", KLIENT_ID)
            append("Korrelasjonsid", korrId)
            append(HttpHeaders.Authorization, "Bearer $token")
        }
    }

    private suspend fun HttpResponse.handleError(sekvensnr: Int, korrId: String): HttpResponse? {
        if (isSuccessful()) return this

        if (status.isClientError() || status.isServerError()) {
            val errorMessage = body<SkeErrorMessage>()
            slackService.addError("$status", "Kunne ikke få trekk for sekvensnr=$sekvensnr: korrId=$korrId ${errorMessage.description()}")
        }
        return null
    }

    private suspend fun HttpResponse.toTrekkpaalegg(sekvensnr: Int? = null, korrId: String) =
        try {
            val text = bodyAsText()
            if (text.isEmpty()) {
                throw IllegalStateException("Empty response from")
            }
            text.validateString(true)

            jsonConfig.decodeFromString<List<Trekkpaalegg>>(text).also {
                if (it.isNotEmpty()) {
                    slackService.addError("Data fra Skatteetaten", "Hentet ${it.size} trekk sekvensnummer=$sekvensnr")
                }
            }
        } catch (e: JsonConvertException) {
            logger.warn { "Feil i konvertering av response: ${e.message} korrId=$korrId" }
            slackService.addError("JsonConvertException", "Feil i konvertering av response: ${e.message} korrId=$korrId")
            emptyList()
        } catch (e: IllegalArgumentException) {
            logger.warn { "Feil i konvertering av response: ${e.message} korrId=$korrId" }
            slackService.addError("JsonConvertException", "Feil i konvertering av response: ${e.message} korrId=$korrId")
            emptyList()
        } catch (e: IllegalStateException) {
            logger.warn { "Tom body i response: ${e.message} korrId=$korrId" }
            slackService.addError("Manglende data", "Tom body i response: ${e.message} Fikk ingen data for sekvensnummer=$sekvensnr korrId=$korrId")
            emptyList()
        }
}