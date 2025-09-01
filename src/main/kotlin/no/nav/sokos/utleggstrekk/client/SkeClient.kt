package no.nav.sokos.utleggstrekk.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.url
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import no.nav.sokos.utleggstrekk.config.PropertiesConfig
import no.nav.sokos.utleggstrekk.domene.ske.Trekkpaalegg
import no.nav.sokos.utleggstrekk.security.maskinporten.MaskinportenAccessTokenClient
import no.nav.sokos.utleggstrekk.utils.toTrekkpaalegg
import java.util.UUID

private const val maxAntall = 2500
private const val KLIENT_ID = "NAV/0.1"

class SkeClient(
    private val client: HttpClient = httpClient,
    private val tokenProvider: MaskinportenAccessTokenClient = MaskinportenAccessTokenClient(PropertiesConfig.MaskinportenClientConfig(), client),
) {
    val basePath = PropertiesConfig.SKEConfig().skeRestUrl

    suspend fun hentAlleUtleggstrekk(): List<Trekkpaalegg> {
        return client.get {
            url(basePath)
            headers(commonHeaders())
        }.toTrekkpaalegg()
    }

    suspend fun hentUtleggstrekkFraSekvensnr(sekvensnr: Int): List<Trekkpaalegg> {
        val body = client.get {
            url("${basePath}?fraSekvensnummer=$sekvensnr&maksAntall=$maxAntall")
            headers(commonHeaders())
        }.also { response -> println(response.status.toString() + response.body()) }
        return body.toTrekkpaalegg()
    }

    private suspend fun commonHeaders(): HeadersBuilder.() -> Unit  {
        val token = tokenProvider.hentAccessToken()
        return {
            append("Klientid", KLIENT_ID)
            append("Korrelasjonsid", UUID.randomUUID().toString())
            append(HttpHeaders.Authorization, "Bearer $token")
        }
    }
}
