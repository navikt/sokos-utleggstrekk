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
import jakarta.jms.MessageFormatException
import jakarta.jms.Queue
import mu.KotlinLogging

import no.nav.sokos.utleggstrekk.config.MQConfig
import no.nav.sokos.utleggstrekk.config.PropertiesConfig
import no.nav.sokos.utleggstrekk.config.TEAM_LOGS_MARKER
import no.nav.sokos.utleggstrekk.config.jsonConfig
import no.nav.sokos.utleggstrekk.database.PostgresDataSource
import no.nav.sokos.utleggstrekk.database.Repository
import no.nav.sokos.utleggstrekk.database.model.KvitteringStatus
import no.nav.sokos.utleggstrekk.domene.nav.ErrorCategory
import no.nav.sokos.utleggstrekk.domene.nav.ErrorHeader
import no.nav.sokos.utleggstrekk.domene.nav.ErrorHeader.PROCESSING_FEIL
import no.nav.sokos.utleggstrekk.domene.nav.KvitteringFraOppdrag
import no.nav.sokos.utleggstrekk.domene.nav.validate
import no.nav.sokos.utleggstrekk.metrics.Metrics.trekkAvvistAvOs
import no.nav.sokos.utleggstrekk.metrics.Metrics.trekkKvittertForAvOS
import no.nav.sokos.utleggstrekk.service.SlackService
import no.nav.sokos.utleggstrekk.utils.Validation.validateString

class JmsListenerService(
    private val repository: Repository = Repository(PostgresDataSource.dataSource),
    private val slackService: SlackService = SlackService.instance,
    val osKvitteringQueue: Queue =
        MQQueue(PropertiesConfig.mqProperties.replyQueueName).apply {
            targetClient = WMQConstants.WMQ_CLIENT_NONJMS_MQ
        },
    private val producerBoq: JmsProducerService =
        JmsProducerService(
            targetQueue =
                MQQueue(PropertiesConfig.mqProperties.replyBoqQueueName).apply {
                    targetClient = WMQConstants.WMQ_CLIENT_NONJMS_MQ
                },
        ),
    connectionFactory: ConnectionFactory = MQConfig.connectionFactory(),
) {
    private val logger = KotlinLogging.logger {}

    private val jmsContext: JMSContext = connectionFactory.createContext(JMSContext.CLIENT_ACKNOWLEDGE)

    init {
        jmsContext.start()
        jmsContext.setExceptionListener { logger.error(TEAM_LOGS_MARKER, "Feil på MQ-kommunikasjon", it) }
        jmsContext.createConsumer(osKvitteringQueue).setMessageListener { onReceipt(it) }
    }

    private fun onReceipt(message: Message) {
        runCatching {
            val jmsMessage = message.getBody(String::class.java)
            jmsMessage.validateString(true)
            val receipt = jsonConfig.decodeFromString<KvitteringFraOppdrag>(jmsMessage)
            receipt.validate()
            processReceipt(receipt)
            message.acknowledge()
        }.onFailure { exception ->
            val messageId = message.jmsMessageID
            logger.error(TEAM_LOGS_MARKER, "$PROCESSING_FEIL $messageId", exception)
            slackService.addError(PROCESSING_FEIL, exception::class.simpleName ?: "Ukjent feil", messageId)

            if (exception !is MessageFormatException) {
                val jmsMessage = message.getBody(String::class.java)
                producerBoq.send(jmsMessage)
                message.acknowledge()
            }
        }

        CoroutineScope(SupervisorJob() + Default).launch {
            slackService.sendCachedErrors(ErrorCategory.KVITTERING_FEIL)
        }
    }

    private fun processReceipt(receipt: KvitteringFraOppdrag) {
        val kvitteringStatus = KvitteringStatus.fromValue(receipt.mmel?.alvorlighetsgrad)

        repository.updateReceiptStatusOfTransaksjon(
            receipt.dokument.transaksjonsId,
            kvitteringStatus,
            receipt.dokument.innrapporteringTrekk.navTrekkId,
        )

        if (kvitteringStatus == KvitteringStatus.FEIL) {
            repository.insertFeilmeldingFraOS(receipt)
            logError(receipt)
            trekkAvvistAvOs.inc()
        } else {
            trekkKvittertForAvOS.inc()
        }
    }

    private fun logError(receipt: KvitteringFraOppdrag) {
        val personnalNumberMatcher = Regex("\\d{11}")
        val errorDescription = receipt.mmel?.beskrMelding?.replace(personnalNumberMatcher, "[fødselsnummer]")

        val message =
            "Trekk med kreditorstrekkID: ${receipt.dokument.innrapporteringTrekk.kreditorTrekkId}, " +
                "corrid: ${receipt.dokument.transaksjonsId} har feilkode: ${receipt.mmel?.kodeMelding} og beskrivelse: $errorDescription"

        logger.warn(message)
        slackService.addError(ErrorHeader.KVITTERING_FEIL, message)
    }
}
