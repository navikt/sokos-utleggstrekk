package no.nav.sokos.utleggstrekk.service

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import mu.KotlinLogging
import no.nav.sokos.utleggstrekk.client.SkeClient
import no.nav.sokos.utleggstrekk.config.PropertiesConfig
import no.nav.sokos.utleggstrekk.httpClient
import no.nav.sokos.utleggstrekk.security.maskinporten.MaskinportenAccessTokenClient
import no.nav.sokos.utleggstrekk.service.DatabaseService

private val logger = KotlinLogging.logger {  }
class UtleggstrekkService(
    databaseService: DatabaseService
) {

    val tokenProvider = MaskinportenAccessTokenClient(PropertiesConfig.MaskinportenClientConfig(), httpClient)
    val skeClient = SkeClient(tokenProvider)

    suspend fun hentAlle(): HttpResponse {
        println("skeClient.hentalle kalles:")
        return skeClient.hentAlleNyeUtleggstrekk()
    }

}