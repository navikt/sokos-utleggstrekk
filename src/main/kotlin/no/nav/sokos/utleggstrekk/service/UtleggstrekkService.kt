package no.nav.sokos.utleggstrekk.service

import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.JsonConvertException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import no.nav.sokos.utleggstrekk.client.SkeClient
import no.nav.sokos.utleggstrekk.domene.ske.Trekkpaalegg
import no.nav.sokos.utleggstrekk.domene.toTrekkDokument
import no.nav.sokos.utleggstrekk.mq.MqProducer

private val logger = KotlinLogging.logger { }

class UtleggstrekkService(
    private val databaseService: DatabaseService,
    private val skeClient: SkeClient = SkeClient(),
    private val mqProducer: MqProducer = MqProducer(),
) {
    suspend fun behandleUtleggstrekk(): Int = lagreNyeUtleggstrekk().run { sendTrekkTilOS() }

    private suspend fun lagreNyeUtleggstrekk() {
        val body = skeClient.hentAlleUtleggstrekk()
        println("Inne i lagre:\n${body.bodyAsText()}")
            body.toUtleggsTrekk().also(::println)
            .mapNotNull { it.takeIf { !databaseService.trekkFinnes(it.trekkid, it.sekvensnummer, it.trekkversjon) } }
            .also {
                println("Det er ${it.size} som skal lagres")
                databaseService.lagreUtleggstrekk(it)
            }
    }
    private fun sendTrekkTilOS(): Int {
        val trekkTilSending = databaseService.hentAlleTrekkSomIkkeErSendt()

        val trekkSomJsonListe = trekkTilSending.map { trekk ->
            val perioder = databaseService.hentPerioderForTrekk(trekk)
            val trekkDomument = trekk.toTrekkDokument(perioder)
            println(trekkDomument)

            Json.encodeToString(trekkDomument).also { println(it) }
        }
        runCatching {
            trekkSomJsonListe.forEach {
                mqProducer.send(it)
            }
        }.onSuccess {
            trekkSomJsonListe.forEach {
                // gjøre dette etter at vi har mottatt kvittering?
                //databaseService.oppdaterTrekkStatus(it)
            }
            return trekkSomJsonListe.size // for test api
        }
        return 0 // for test api
    }


    private suspend fun HttpResponse.toUtleggsTrekk() =
        try {
            body<List<Trekkpaalegg>>().also { println("JSON ER KOVERTERT:\n") }
        } catch (e: JsonConvertException) {
            logger.error { "Feil i konvertering av response: ${e.message}" }
            emptyList()
        }

    // Brukes kun av testAPI
    suspend fun hentAlleNyeUtleggstrekk(): List<Trekkpaalegg> {
        println("hent alle NYE!")
        val sisteSekvensnr = databaseService.hentSisteSekvensnummer()
        println("Henter fra siste sekvensnr: $sisteSekvensnr")
        return hentUtleggstrekkFraSekvensnrOgLagreAlleNye(sisteSekvensnr)
    }

    suspend fun hentAlleNye() = skeClient.hentAlleUtleggstrekk().body<List<Trekkpaalegg>>()

    // Brukes kun av testAPI
    suspend fun hentUtleggstrekkFraSekvensnrOgLagreAlleNye(sekvensnr: Int): List<Trekkpaalegg> {
        println("henter allefra sekvensnr sekvensnr: $sekvensnr")
        val trekkListe = skeClient.hentUtleggstrekkFraSekvensnr(sekvensnr).toUtleggsTrekk()
        return trekkListe.mapNotNull { it.takeIf { !databaseService.trekkFinnes(it.trekkid, it.sekvensnummer, it.trekkversjon) } }
    }
}