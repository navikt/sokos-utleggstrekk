package no.nav.sokos.utleggstrekk.service

import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.serialization.JsonConvertException
import mu.KotlinLogging
import no.nav.sokos.utleggstrekk.client.SkeClient
import no.nav.sokos.utleggstrekk.config.PropertiesConfig
import no.nav.sokos.utleggstrekk.domene.ske.Utleggstrekk
import no.nav.sokos.utleggstrekk.mq.MqProducer

private val logger = KotlinLogging.logger { }

class UtleggstrekkService(
    private val databaseService: DatabaseService,
    private val skeClient: SkeClient = SkeClient(),
    private val mqProducer: MqProducer = MqProducer(PropertiesConfig.MqProperties())
) {


    suspend fun hentAlleUtleggstrekk(): List<Utleggstrekk> {
        println("skeClient.hentalle kalles:")
        val nyeTrekkListe = utleggstrekkResponseToList(skeClient.hentAlleUtleggstrekk())
        println("Antall trekk mottatt: ${nyeTrekkListe.size}")
        lagreAlleNyeUtleggstrekk(nyeTrekkListe)
        val sendTrekkListe = databaseService.hentAlleTrekkSomIkkeErSendt()
        println("Antall trekk lest fra db: ${sendTrekkListe.size}")
        val xmlList = sendTrekkListe.map {
            XmlService.createTrekkXmlObjects(it)
        }
        println("Sender ${xmlList.size} til MQ")
        XmlService.generateXmlStringListFromTrekkXmlList(xmlList).forEach { mqProducer.send(it) }
        mqProducer.commit()
        return nyeTrekkListe  //TODO for testapiet vårt, bør tas bort/endres? etterhvert
    }

    suspend fun hentAlleNyeUtleggstrekk(): List<Utleggstrekk> {
        println("hent alle NYE!")
        val sisteSekvensnr = databaseService.hentSisteSekvensnummer()
        println("Henter fra siste sekvensnr: $sisteSekvensnr")
        return hentUtleggstrekkFraSekvensnrOgLagreAlleNye(sisteSekvensnr)
    }

    suspend fun hentUtleggstrekkFraSekvensnrOgLagreAlleNye(sekvensnr: Int): List<Utleggstrekk> {
        println("henter allefra sekvensnr sekvensnr: $sekvensnr")
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
        }.filterNotNull().also { println("Etter filtrering av de vi har fra før : ${it.size}") }.also { databaseService.lagreAlleNyeUtleggstrekk(it) }

}