package no.nav.sokos.utleggstrekk.service

import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.serialization.JsonConvertException
import mu.KotlinLogging
import no.nav.sokos.utleggstrekk.client.SkeClient
import no.nav.sokos.utleggstrekk.domene.ske.Utleggstrekk

private val logger = KotlinLogging.logger { }

class UtleggstrekkService(
    private val databaseService: DatabaseService,
    private val skeClient: SkeClient = SkeClient()
) {

    suspend fun hentAlleNyeUtleggstrekk(): List<Utleggstrekk> {
        val response = skeClient.hentAlleNyeUtleggstrekk()
        return try {
            response.body<List<Utleggstrekk>>()
        } catch (e: JsonConvertException) {
            logger.error { "Feil i konvertering av response: ${e.message}" }
            emptyList()
        }
    }

    suspend fun hentAlle(): HttpResponse {
        println("skeClient.hentalle kalles:")
        return skeClient.hentAlleNyeUtleggstrekk()
    }

}