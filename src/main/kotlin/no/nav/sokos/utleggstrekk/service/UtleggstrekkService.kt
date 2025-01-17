package no.nav.sokos.utleggstrekk.service

import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.JsonConvertException
import mu.KotlinLogging
import no.nav.sokos.utleggstrekk.client.SkeClient
import no.nav.sokos.utleggstrekk.domene.ske.Trekkpaalegg
import no.nav.sokos.utleggstrekk.mq.MQProducerService

private val logger = KotlinLogging.logger { }

class UtleggstrekkService(
    private val databaseService: DatabaseService,
    private val genererTrekkService: GenererTrekkService,
    private val skeClient: SkeClient = SkeClient(),
    private val mqProducer: MQProducerService = MQProducerService(),
) {
    suspend fun behandleUtleggstrekk(): Int = lagreNyeUtleggstrekk().run { sendTrekkTilOS() }

    private suspend fun lagreNyeUtleggstrekk() {
        val body = skeClient.hentAlleUtleggstrekk()
        println(body.bodyAsText())
            body.toUtleggsTrekk().also(::println)
            .mapNotNull { it.takeIf { !databaseService.trekkFinnes(it.trekkid, it.sekvensnummer, it.trekkversjon) } }
            .also {
                databaseService.lagreUtleggstrekk(it)
            }
    }
    private fun sendTrekkTilOS(): Int {
        databaseService.hentAlleTrekkSomIkkeErSendt().run {
            val trekkSomXmlObjekter = genererTrekkService.lagTrekkTilOs(this)
            val trekkSomXml = trekkSomXmlObjekter.map { NyXmlService.xmlOf(it) }

            runCatching {
                mqProducer.send(trekkSomXml)
            }.onSuccess {
                trekkSomXmlObjekter.forEach {
                    // gjøre dette etter at vi har mottatt kvittering?
                    databaseService.oppdaterTrekkStatus(it)
                }
                return trekkSomXmlObjekter.size // for test api
            }
        }
        return 0 // for test api
    }

    private suspend fun HttpResponse.toUtleggsTrekk() =
        try {
            body<List<Trekkpaalegg>>().also { println(it) }
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