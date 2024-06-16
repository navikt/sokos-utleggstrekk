package no.nav.sokos.utleggstrekk.service

import mu.KotlinLogging
import no.nav.sokos.utleggstrekk.client.SkeClient
import no.nav.sokos.utleggstrekk.config.PropertiesConfig
import no.nav.sokos.utleggstrekk.httpClient
import no.nav.sokos.utleggstrekk.security.maskinporten.MaskinportenAccessTokenClient
import no.nav.sokos.utleggstrekk.service.DatabaseService

private val logger = KotlinLogging.logger {  }
class UtleggstrekkService(
) {

    val tokenProvider = MaskinportenAccessTokenClient(PropertiesConfig.MaskinportenClientConfig(), httpClient)
    val skeClient = SkeClient(tokenProvider, PropertiesConfig.SKEConfig().skeRestUrl)

    suspend fun hentAlle(){
        println("skeClient.hentalle kalles:")
        skeClient.hentAlleNyeUtleggstrekk()
        println("skeClient.hentalle Ferdig:")
    }

}