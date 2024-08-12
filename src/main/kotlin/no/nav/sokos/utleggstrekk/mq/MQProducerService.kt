package no.nav.sokos.utleggstrekk.mq

import com.ibm.mq.jms.MQQueue
import com.ibm.msg.client.jms.JmsConstants
import mu.KotlinLogging
import no.nav.sokos.utleggstrekk.config.PropertiesConfig
import javax.jms.ConnectionFactory
import javax.jms.JMSContext
import javax.jms.Queue

class MQProducerService(
    private val connectionFactory: ConnectionFactory = MQConfig.connectionFactory(),
) {
    private val jmsContext: JMSContext = connectionFactory.createContext()
    private val logger = KotlinLogging.logger {}
    private val properties = PropertiesConfig.MqProperties()

    fun send(payload: List<String>, queue: Queue = MQQueue(properties.queueName), replyQueue: Queue = MQQueue(properties.replyQueueName)) {
        jmsContext.createContext(JmsConstants.SESSION_TRANSACTED).use { context ->
            runCatching {
                val producer = context.createProducer().apply { jmsReplyTo = replyQueue }
                payload.forEach { message ->
                    val jmsMessage = context.createTextMessage(message)
                    producer.send(queue, jmsMessage)
                    logger.info { "Sendt melding til OppdragZ, messageId: ${jmsMessage.jmsMessageID}, payload: $message" }
                }
            }.onSuccess {
                context.commit()
                logger.info { "MQ-transaksjon committed message" }
            }.onFailure { exception ->
                context.rollback()
                logger.error(exception) { "MQ-transaksjon rolled back" }
                throw exception
            }
        }
    }
}