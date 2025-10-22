package no.nav.sokos.utleggstrekk.service

import kotlinx.serialization.json.Json

import com.ibm.mq.jakarta.jms.MQQueue
import com.ibm.msg.client.jakarta.wmq.WMQConstants
import mu.KotlinLogging

import no.nav.sokos.utleggstrekk.client.SkeClient
import no.nav.sokos.utleggstrekk.config.PropertiesConfig
import no.nav.sokos.utleggstrekk.database.model.UtleggstrekkStatus.SENDT
import no.nav.sokos.utleggstrekk.database.model.UtleggstrekkTable
import no.nav.sokos.utleggstrekk.domene.nav.TrekkTilOppdrag
import no.nav.sokos.utleggstrekk.domene.ske.Trekkpaalegg
import no.nav.sokos.utleggstrekk.mq.JmsListenerService
import no.nav.sokos.utleggstrekk.mq.JmsProducerService

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
    private val logger = KotlinLogging.logger { }

    val jsonConfig =
        Json {
            explicitNulls = false
            encodeDefaults = true
        }

    // Eksempel funksjon som kalles i schedulering
    suspend fun run() {
        val nyeUtleggsTrekk = hentUtleggsTrekk()
        databaseService.lagreUtleggstrekk(nyeUtleggsTrekk)
        val trekktilSending = behandleTrekkService.lagTrekkSomSkalSendes()
        logger.info { "Det er ${trekktilSending.size} trekk som skal sendes" }
        sendTrekkTilOS(trekktilSending)
    }

    private suspend fun hentUtleggsTrekk(): List<Trekkpaalegg> {
        val sisteSekvensnr = databaseService.hentSisteSekvensnummer()
        return skeClient.hentUtleggstrekkFraSekvensnr(sisteSekvensnr)
    }

    // TODO: refaktorere
    // TODO: Felles Jsonobjekt MEN skriv tester først
    fun sendTrekkTilOS(trekkTilOppdragMap: Map<UtleggstrekkTable, List<TrekkTilOppdrag>>) =
        trekkTilOppdragMap
            .forEach {
                val dokumentListe = it.value.map { dokument -> jsonConfig.encodeToString(dokument) }
                dokumentListe.forEach { dokument ->
                    mqProducer.send(dokument)
                }
                databaseService.oppdaterTrekkStatus(it.key.corrid, SENDT)
            }
}
