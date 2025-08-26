package no.nav.sokos.utleggstrekk.mq

import com.ibm.mq.jakarta.jms.MQQueue
import com.ibm.msg.client.jakarta.wmq.WMQConstants
import jakarta.jms.ConnectionFactory
import jakarta.jms.JMSContext
import jakarta.jms.Message
import jakarta.jms.Queue
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import no.nav.sokos.utleggstrekk.config.MQConfig
import no.nav.sokos.utleggstrekk.config.PropertiesConfig
import no.nav.sokos.utleggstrekk.domene.nav.TrekkTilOppdrag
import no.nav.sokos.utleggstrekk.service.DatabaseService

class JmsListenerService(
    private val databaseService: DatabaseService = DatabaseService(),
    val osKvitteringQueue: Queue =
        MQQueue(PropertiesConfig.MQProperties().replyQueueName).apply {
            targetClient = WMQConstants.WMQ_CLIENT_NONJMS_MQ
        },
    connectionFactory: ConnectionFactory = MQConfig.connectionFactory(),
) {
    private val logger = KotlinLogging.logger {}

    private val jmsContext: JMSContext = connectionFactory.createContext(JMSContext.CLIENT_ACKNOWLEDGE)
    private val json = Json { ignoreUnknownKeys = true }

    init {
        jmsContext.start()
        jmsContext.setExceptionListener { logger.error("Feil på MQ-kommunikasjon", it) }
        jmsContext.createConsumer(osKvitteringQueue).setMessageListener { onReceipt(it) }
    }

    private fun onReceipt(message: Message) {
        val jmsMessage = message.getBody(String::class.java)
        runCatching {
            logger.info { "Mottatt kvitteringmelding fra OppdragZ: $jmsMessage" }
            val receipt = json.decodeFromString<TrekkTilOppdrag>(jmsMessage)
            processReceipt(receipt)
            message.acknowledge()
        }.onFailure { exception ->
            logger.error(exception) { "Prosessering av kvitteringmelding feilet. ${message.jmsMessageID}" }
        }
    }

    private fun processReceipt(receipt: TrekkTilOppdrag) {
        databaseService.oppdaterTrekkMedKvitteringsinfo(receipt)
        if (receipt.mmel!!.alvorlighetsgrad != "00") {
            databaseService.lagreFeilkoderFraOS(receipt)
            logError(receipt)
        }
    }

    private fun logError(kvitteringMedFeil: TrekkTilOppdrag) {
        logger.info(
            "Trekk med kreditorstrekkID: ${kvitteringMedFeil.dokument.innrapporteringTrekk.kreditorTrekkId}, " +
                "corrid: ${kvitteringMedFeil.dokument.transaksjonsId} har feilkode: ${kvitteringMedFeil.mmel?.kodeMelding} og beskrivelse: ${kvitteringMedFeil.mmel?.beskrMelding}",
        )

        // TODO sjekke/vurdere om det skal sendes melding til slack og evt utføre det.
    }
}
