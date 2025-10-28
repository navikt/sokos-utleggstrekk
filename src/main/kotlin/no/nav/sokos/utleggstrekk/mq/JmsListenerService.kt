package no.nav.sokos.utleggstrekk.mq

import com.ibm.mq.jakarta.jms.MQQueue
import com.ibm.msg.client.jakarta.wmq.WMQConstants
import com.zaxxer.hikari.HikariDataSource
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
import no.nav.sokos.utleggstrekk.service.withTransaction

class JmsListenerService(
    private val dataSource: HikariDataSource = PostgresDataSource.dataSource,
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
            logger.error(exception) { "Prosessering av kvitteringmelding feilet. ${message.jmsMessageID}" }
        }
    }

    private fun processReceipt(receipt: KvitteringFraOppdrag) {
        val kvitteringStatus = KvitteringStatus.fromValue(receipt.mmel?.alvorlighetsgrad)
        dataSource.withTransaction { session ->
            println("OPPDATERER TRANSAKSJON ${receipt.dokument.transaksjonsId} med status $kvitteringStatus OG NAVTREKKID ${receipt.dokument.innrapporteringTrekk.navTrekkId}")
            RepositoryNy.updateTransaksjon(receipt.dokument.transaksjonsId, kvitteringStatus, receipt.dokument.innrapporteringTrekk.navTrekkId ?: "Ingen TrekkId i kvittering", session)

            if (kvitteringStatus == KvitteringStatus.FEIL) {
                RepositoryNy.insertFeilmeldingFraOS(receipt, session)
                logError(receipt)
            }
        }
    }

    private fun logError(receipt: KvitteringFraOppdrag) {
        logger.info(
            "Trekk med kreditorstrekkID: ${receipt.dokument.innrapporteringTrekk.kreditorTrekkId}, " +
                "corrid: ${receipt.dokument.transaksjonsId} har feilkode: ${receipt.mmel?.kodeMelding} og beskrivelse: ${receipt.mmel?.beskrMelding}",
        )

        // TODO sjekke/vurdere om det skal sendes melding til slack og evt utføre det.
    }
}
