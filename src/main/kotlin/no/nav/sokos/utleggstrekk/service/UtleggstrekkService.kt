package no.nav.sokos.utleggstrekk.service

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import no.nav.sokos.utleggstrekk.client.SkeClient
import no.nav.sokos.utleggstrekk.database.model.UtleggstrekkTable
import no.nav.sokos.utleggstrekk.domene.nav.TrekkTilOppdrag
import no.nav.sokos.utleggstrekk.domene.ske.Trekkpaalegg
import no.nav.sokos.utleggstrekk.mq.MqProducer
import no.nav.sokos.utleggstrekk.utils.toTrekkpaalegg



private const val SENDT = "SENDT"

class UtleggstrekkService(
    private val databaseService: DatabaseService,
    private val behandleTrekkService: BehandleTrekkService,
    private val skeClient: SkeClient = SkeClient(),
    private val mqProducer: MqProducer = MqProducer(),
) {
private val logger = KotlinLogging.logger {  }
    suspend fun HentOgSendUtleggstrekk(): Int {
        hentOgLagreNyeUtleggstrekk()
        val trekktilSending = behandleTrekkService.lagTrekkSomSkalSendes()
        return sendTrekkTilOS(trekktilSending)
    }

    suspend fun hentOgLagreNyeUtleggstrekk() {
        val nyeTrekk = skeClient.hentAlleUtleggstrekk()
        nyeTrekk.also { logger.info { "Hentet ${it.size} utleggstrekk fra Skatt" } }
            .filterNot { databaseService.trekkFinnes(it.trekkid, it.sekvensnummer, it.trekkversjon) }
            .let {
                logger.info("Det er ${it.size} som skal lagres")
                databaseService.lagreUtleggstrekk(it)
            }
    }
    private fun sendTrekkTilOS(trekkTilOppdragPairList: List<Pair<UtleggstrekkTable, List<TrekkTilOppdrag>>>): Int {
        return trekkTilOppdragPairList.map { tilOsPair ->
            val dokument = tilOsPair.second.map { Json.encodeToString(it) }
            logger.info("sender trekkid: ${tilOsPair.first.trekkidSke} versjon: ${tilOsPair.first.trekkversjon} sekvensnummer: ${tilOsPair.first.sekvensnummer}")
            dokument.forEach {
                println(it)
                //mqProducer.send(it)
            }
            databaseService.oppdaterTrekkStatus(tilOsPair.first.corrid, SENDT)
        }.size
    }


    suspend fun hentAlleNyeUtleggstrekk(): List<Trekkpaalegg> {
        val sisteSekvensnr = databaseService.hentSisteSekvensnummer()
        println("Henter fra siste sekvensnr: $sisteSekvensnr")
        return hentUtleggstrekkFraSekvensnrOgLagreAlleNye(sisteSekvensnr)
    }

    suspend fun hentAlleUtleggstrekk() = skeClient.hentAlleUtleggstrekk()

    suspend fun hentUtleggstrekkFraSekvensnrOgLagreAlleNye(sekvensnr: Int): List<Trekkpaalegg> {
        println("henter allefra sekvensnr sekvensnr: $sekvensnr")
        val trekkListe = skeClient.hentUtleggstrekkFraSekvensnr(sekvensnr).toTrekkpaalegg()
        return trekkListe.filterNot { databaseService.trekkFinnes(it.trekkid, it.sekvensnummer, it.trekkversjon) }
        //TODO Må lagre også
    }
}


