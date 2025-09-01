package no.nav.sokos.utleggstrekk.mq

import javax.jms.Connection
import javax.jms.MessageProducer
import javax.jms.Session

import com.ibm.mq.jms.MQConnectionFactory
import com.ibm.mq.jms.MQQueue
import com.ibm.msg.client.jms.JmsConstants
import com.ibm.msg.client.wmq.WMQConstants
import mu.KotlinLogging

import no.nav.sokos.utleggstrekk.config.PropertiesConfig

private val logger = KotlinLogging.logger {}

class MqProducer(
    private val config: PropertiesConfig.MqProperties = PropertiesConfig.MqProperties(),
) {
    private lateinit var session: Session
    private lateinit var mqProducer: MessageProducer
    private var connected: Boolean = false

    private val logger = KotlinLogging.logger { }

    init {
        connect()
    }

    private fun connect() {
        logger.info("Connecting to MQ queue ${config.queueName}")
        val connection = config.connect()
        session = connection.createSession(Session.SESSION_TRANSACTED)
        val queue =
            (session.createQueue(config.queueName) as MQQueue).apply {
                targetClient = WMQConstants.WMQ_CLIENT_NONJMS_MQ
            }
        mqProducer = session.createProducer(queue)
        connection.start()
        connected = true
    }

    fun send(message: String): Boolean =
        try {
            logger.info("Sender utleggstrekk til oppdrag \n$message")
            if (!connected) connect()
            val jmsMessage = session.createTextMessage(message)
            val replyQueue =
                (session.createQueue(config.replyQueueName) as MQQueue).apply {
                    targetClient = WMQConstants.WMQ_CLIENT_NONJMS_MQ
                }
            jmsMessage.jmsReplyTo = replyQueue
            mqProducer.send(jmsMessage)
            commit()
            true
        } catch (ex: Exception) {
            logger.error("Feilet ved sending via MQ til OS")
            connected = false
            false
        }

    fun commit() = session.commit()

    private fun PropertiesConfig.MqProperties.connect(): Connection =
        MQConnectionFactory()
            .also {
                it.transportType = WMQConstants.WMQ_CM_CLIENT
                it.hostName = hostName
                it.port = port
                it.channel = channel
                it.queueManager = queueManagerName
                it.targetClientMatching = true
                it.setBooleanProperty(JmsConstants.USER_AUTHENTICATION_MQCSP, true)
            }.createConnection(username, password)
}
