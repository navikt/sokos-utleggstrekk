package no.nav.sokos.utleggstrekk.mq

import com.ibm.msg.client.jakarta.jms.JmsConstants.SESSION_TRANSACTED
import jakarta.jms.ConnectionFactory
import jakarta.jms.JMSContext
import jakarta.jms.JMSProducer
import jakarta.jms.Queue
import mu.KotlinLogging

import no.nav.sokos.utleggstrekk.config.MQConfig
import no.nav.sokos.utleggstrekk.config.TEAM_LOGS_MARKER
import no.nav.sokos.utleggstrekk.metrics.Metrics.trekkSendtTilOs

class JmsProducerService(
    private val targetQueue: Queue,
    private val replyQueue: Queue,
    connectionFactory: ConnectionFactory = MQConfig.connectionFactory(),
) {
    private val logger = KotlinLogging.logger {}
    private val jmsContext: JMSContext = connectionFactory.createContext(SESSION_TRANSACTED)
    private val producer: JMSProducer = jmsContext.createProducer().apply { jmsReplyTo = replyQueue }

    init {
        jmsContext.setExceptionListener {
            logger.error { "Feil på MQ-kommunikasjon: ${it.message}" }
            logger.error(TEAM_LOGS_MARKER, "Feil på MQ-kommunikasjon", it)
        }
    }

    // TODO: Må legge inn feilhåndtering + manuell håndtering
    fun send(payload: String) {
        val message = jmsContext.createTextMessage(payload)
        try {
            producer.send(targetQueue, message)
            jmsContext.commit()
            trekkSendtTilOs.inc()
        } catch (exception: Exception) {
            logger.error { "MQ-transaksjon feilet. ${message.jmsMessageID}" }
            logger.error(TEAM_LOGS_MARKER, "MQ-transaksjon feilet. ${message.jmsMessageID}", exception)
            jmsContext.rollback()
            logger.error(TEAM_LOGS_MARKER, "MQ-transaksjon rolled back", exception)
            throw Exception(exception.message ?: "Feil ved sending av melding til MQ")
        }
    }
}