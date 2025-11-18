package no.nav.sokos.utleggstrekk.mq

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

import com.ibm.mq.jakarta.jms.MQQueue
import com.ibm.msg.client.jakarta.wmq.WMQConstants
import jakarta.jms.ConnectionFactory
import jakarta.jms.JMSContext
import jakarta.jms.Message
import jakarta.jms.Queue
import mu.KotlinLogging

import no.nav.sokos.utleggstrekk.config.MQConfig
import no.nav.sokos.utleggstrekk.config.PropertiesConfig
import no.nav.sokos.utleggstrekk.config.jsonConfig
import no.nav.sokos.utleggstrekk.database.PostgresDataSource
import no.nav.sokos.utleggstrekk.database.RepositoryNy
import no.nav.sokos.utleggstrekk.database.model.KvitteringStatus
import no.nav.sokos.utleggstrekk.domene.nav.KvitteringFraOppdrag
import no.nav.sokos.utleggstrekk.service.SlackService

class JmsListenerService(
    private val repositoryNy: RepositoryNy = RepositoryNy(PostgresDataSource.dataSource),
    private val slackService: SlackService = SlackService(),
    val osKvitteringQueue: Queue =
        MQQueue(PropertiesConfig.MQProperties().replyQueueName).apply {
            targetClient = WMQConstants.WMQ_CLIENT_NONJMS_MQ
        },
    connectionFactory: ConnectionFactory = MQConfig.connectionFactory(),
) {
    private val logger = KotlinLogging.logger {}

    private val jmsContext: JMSContext = connectionFactory.createContext(JMSContext.CLIENT_ACKNOWLEDGE)

    init {
        jmsContext.start()
        jmsContext.setExceptionListener { logger.error("Feil på MQ-kommunikasjon", it) }
        jmsContext.createConsumer(osKvitteringQueue).setMessageListener { onReceipt(it) }
    }

    private fun onReceipt(message: Message) {
        val jmsMessage = message.getBody(String::class.java)
        try {
            val receipt = jsonConfig.decodeFromString<KvitteringFraOppdrag>(jmsMessage)
            processReceipt(receipt)
            message.acknowledge()
        } catch (exception: Exception) {
            val header = "Prosessering av kvitteringmelding feilet."
            logger.error(exception) { "$header ${message.jmsMessageID}" }
            slackService.addError(header, message.jmsMessageID)
        }

        CoroutineScope(SupervisorJob() + Default).launch {
            slackService.sendErrors("Kvittering fra oppdrag feil")
        }
    }

    private fun processReceipt(receipt: KvitteringFraOppdrag) {
        val kvitteringStatus = KvitteringStatus.fromValue(receipt.mmel?.alvorlighetsgrad)

        repositoryNy.updateTransaksjon(
            receipt.dokument.transaksjonsId,
            kvitteringStatus,
            receipt.dokument.innrapporteringTrekk.navTrekkId,
        )

        if (kvitteringStatus == KvitteringStatus.FEIL) {
            repositoryNy.insertFeilmeldingFraOS(receipt)
            logError(receipt)
        }
    }

    private fun logError(receipt: KvitteringFraOppdrag) {
        val message =
            "Trekk med kreditorstrekkID: ${receipt.dokument.innrapporteringTrekk.kreditorTrekkId}, " +
                "corrid: ${receipt.dokument.transaksjonsId} har feilkode: ${receipt.mmel?.kodeMelding} og beskrivelse: ${receipt.mmel?.beskrMelding}"

        logger.info(message)
        slackService.addError("Kvittering feil", message)
    }
}