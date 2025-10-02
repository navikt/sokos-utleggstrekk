package no.nav.sokos.utleggstrekk.service

import kotlinx.serialization.json.Json

import com.ibm.mq.jakarta.jms.MQQueue
import com.ibm.msg.client.jakarta.wmq.WMQConstants

import no.nav.sokos.utleggstrekk.client.SkeClient
import no.nav.sokos.utleggstrekk.config.PropertiesConfig
import no.nav.sokos.utleggstrekk.database.model.UtleggstrekkTable
import no.nav.sokos.utleggstrekk.domene.nav.TrekkTilOppdrag
import no.nav.sokos.utleggstrekk.mq.JmsListenerService
import no.nav.sokos.utleggstrekk.mq.JmsProducerService
import no.nav.sokos.utleggstrekk.utils.logger

const val SENDT = "SENDT"

class UtleggsTrekkService(
    private val databaseService: DatabaseService = DatabaseService(),
    private val behandleTrekkService: BehandleTrekkService = BehandleTrekkService(databaseService),
    private val skeClient: SkeClient = SkeClient(),
    private val mqProducer: JmsProducerService =
        JmsProducerService(
            targetQueue =
                MQQueue(PropertiesConfig.MQProperties().queueName).apply {
                    targetClient = WMQConstants.WMQ_CLIENT_NONJMS_MQ
                },
            replyQueue =
                JmsListenerService().osKvitteringQueue,
        ),
) {
    suspend fun hentOgSendUtleggstrekk(): Int {
        logger.info("Henter utleggstrekkfra skatt ")
        hentOgLagreNyeUtleggstrekk()
        val trekktilSending = behandleTrekkService.lagTrekkSomSkalSendes()
        return sendTrekkTilOS(trekktilSending)
    }

    suspend fun hentOgLagreNyeUtleggstrekk() {
        // TODO Denne henter alle hver gang, Den bør bare hente nye når den skal brukes mot skatt regelmessig
        val nyeTrekkListe = skeClient.hentAlleUtleggstrekk() // TODO endre til å kalle hentAlleNyeUtleggstrekk()
        nyeTrekkListe
            .also { logger.info { "Hentet ${it.size} utleggstrekk fra Skatt" } }
            .filterNot { databaseService.trekkFinnes(it.trekkid, it.sekvensnummer, it.trekkversjon) }
            .let {
                logger.info("Det er ${it.size} som skal lagres")
                databaseService.lagreUtleggstrekk(it)
            }
    }

    // TODO: refaktorere
    fun sendTrekkTilOS(trekkTilOppdragMap: Map<UtleggstrekkTable, List<TrekkTilOppdrag>>): Int =
        trekkTilOppdragMap
            .map {
                val dokumentListe = it.value.map { Json.encodeToString(it) }
                logger.info("sender trekkid: ${it.key.trekkidSke} versjon: ${it.key.trekkversjon} sekvensnummer: ${it.key.sekvensnummer}")
                dokumentListe.forEach { dokument ->
                    mqProducer.send(dokument)
                }
                databaseService.oppdaterTrekkStatus(it.key.corrid, SENDT)
            }.size

    // Ikke fjern :)
    suspend fun hentAlleNyeUtleggstrekk() {
        val sisteSekvensnr = databaseService.hentSisteSekvensnummer()
        logger.info("Henter fra siste sekvensnr: $sisteSekvensnr")
        hentUtleggstrekkFraSekvensnrOgLagreAlleNye(sisteSekvensnr)
    }

    suspend fun hentUtleggstrekkFraSekvensnrOgLagreAlleNye(sekvensnr: Int) {
        skeClient
            .hentUtleggstrekkFraSekvensnr(sekvensnr)
            .also { logger.info { "Hentet ${it.size} utleggstrekk fra Skatt" } }
            .let { databaseService.lagreUtleggstrekk(it) }
    }
}