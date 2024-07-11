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
    private val skeClient: SkeClient = SkeClient(),
) {


    suspend fun hentAlleUtleggstrekk(): List<Utleggstrekk> {
        println("skeClient.hentalle kalles:")
        val trekkListe = utleggstrekkResponseToList(skeClient.hentAlleUtleggstrekk())
        return lagreAlleNyeUtleggstrekk(trekkListe)
    }

    suspend fun hentAlleNyeUtleggstrekk(): List<Utleggstrekk> {
        val sisteSekvensnr = databaseService.hentSisteSekvensnummer()
        return hentUtleggstrekkFraSekvensnrOgLagreAlleNye(sisteSekvensnr)
    }

    suspend fun hentUtleggstrekkFraSekvensnrOgLagreAlleNye(sekvensnr: Int): List<Utleggstrekk> {
        val response = skeClient.hentUtleggstrekkFraSekvensnr(sekvensnr)
        val trekkListe = utleggstrekkResponseToList(response)
        return lagreAlleNyeUtleggstrekk(trekkListe)
    }

    private suspend fun utleggstrekkResponseToList(response: HttpResponse): List<Utleggstrekk> {
        return try {
            response.body<List<Utleggstrekk>>()
        } catch (e: JsonConvertException) {
            logger.error { "Feil i konvertering av response: ${e.message}" }
            emptyList()
        }
    }

    private fun lagreAlleNyeUtleggstrekk(trekkListe: List<Utleggstrekk>): List<Utleggstrekk> =
        trekkListe.map {
            it.takeIf{!databaseService.trekkFinnes(it.sekvensnummer) }.also { println("take if: $it") }
        }.filterNotNull().also { println("lagring av Map gir : ${it.size}") }.also { databaseService.lagreAlleNyeUtleggstrekk(it) }
}