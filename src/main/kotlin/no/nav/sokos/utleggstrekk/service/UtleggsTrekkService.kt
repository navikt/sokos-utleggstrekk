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

const val SENDT = "SENDT"

class UtleggsTrekkService(
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
        val nyeTrekkListe = skeClient.hentAlleUtleggstrekk()
        nyeTrekkListe.also { logger.info { "Hentet ${it.size} utleggstrekk fra Skatt" } }
            .filterNot { databaseService.trekkFinnes(it.trekkid, it.sekvensnummer, it.trekkversjon) }
            .let {
                logger.info("Det er ${it.size} som skal lagres")
                databaseService.lagreUtleggstrekk(it)
            }
    }
    fun sendTrekkTilOS(trekkTilOppdragMap: Map<UtleggstrekkTable, List<TrekkTilOppdrag>>): Int {
        return trekkTilOppdragMap.map {
            val dokumentListe = it.value.map { Json.encodeToString(it) }
            logger.info("sender trekkid: ${it.key.trekkidSke} versjon: ${it.key.trekkversjon} sekvensnummer: ${it.key.sekvensnummer}")
            dokumentListe.forEach { dokument ->
                println(dokument)
                mqProducer.send(dokument)
            }
            databaseService.oppdaterTrekkStatus(it.key.corrid, SENDT)
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


