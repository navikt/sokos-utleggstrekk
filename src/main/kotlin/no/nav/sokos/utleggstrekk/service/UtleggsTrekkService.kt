package no.nav.sokos.utleggstrekk.service

import com.ibm.mq.jakarta.jms.MQQueue
import com.ibm.msg.client.jakarta.wmq.WMQConstants
import mu.KotlinLogging

import no.nav.sokos.utleggstrekk.client.SkeClient
import no.nav.sokos.utleggstrekk.config.PropertiesConfig
import no.nav.sokos.utleggstrekk.database.PostgresDataSource
import no.nav.sokos.utleggstrekk.database.RepositoryNy
import no.nav.sokos.utleggstrekk.database.model.TransaksjonOS
import no.nav.sokos.utleggstrekk.database.model.TransaksjonsStatus
import no.nav.sokos.utleggstrekk.domene.ske.Trekkpaalegg
import no.nav.sokos.utleggstrekk.mq.JmsListenerService
import no.nav.sokos.utleggstrekk.mq.JmsProducerService

class UtleggsTrekkService(
    private val repositoryNy: RepositoryNy = RepositoryNy(PostgresDataSource.dataSource),
    private val skeClient: SkeClient = SkeClient(),
    private val mqProducer: JmsProducerService =
        JmsProducerService(
            targetQueue =
                MQQueue(PropertiesConfig.MQProperties().queueName).apply {
                    targetClient = WMQConstants.WMQ_CLIENT_NONJMS_MQ
                },
            replyQueue = JmsListenerService(repositoryNy).osKvitteringQueue,
        ),
) {
    private val logger = KotlinLogging.logger { }

    // Eksempel funksjon som kalles i schedulering
    suspend fun schedule() {
        val nyeUtleggsTrekk: List<Trekkpaalegg> = hentUtleggsTrekk()
        processTrekkpaalegg(nyeUtleggsTrekk)
        BehandleTrekkServiceNy(repositoryNy).behandleTrekk()

        repositoryNy.getTransaksjonerTilOsSomIkkeErSendt().forEach { osTransaksjon -> sendTrekkTilOS(osTransaksjon) }
    }

    private suspend fun hentUtleggsTrekk(): List<Trekkpaalegg> {
        val sisteSekvensnr = repositoryNy.getLastSekvensnummer()
        return skeClient.hentUtleggstrekkFraSekvensnr(sisteSekvensnr)
    }

    private fun processTrekkpaalegg(trekkpaalegg: List<Trekkpaalegg>) {
        trekkpaalegg.forEach { trekk ->
            repositoryNy.saveTrekkpaalegg(trekk)
        }
    }

    private fun sendTrekkTilOS(transaksjonOS: TransaksjonOS) {
        runCatching {
            mqProducer.send(transaksjonOS.documentJson)
        }.onSuccess {
            repositoryNy.updateTransaksjonStatus(transaksjonOS.transaksjonsID, TransaksjonsStatus.SENDT)
        }.onFailure { exception ->
            logger.error(exception) { "Feil ved sending av dokument til OS: ${exception.message}" }
        }
    }
}