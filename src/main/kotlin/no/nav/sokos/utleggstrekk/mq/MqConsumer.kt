package no.nav.sokos.utleggstrekk.mq

import com.ibm.mq.jms.MQConnectionFactory
import com.ibm.mq.jms.MQQueue
import com.ibm.msg.client.jms.JmsConstants
import com.ibm.msg.client.wmq.WMQConstants
import mu.KotlinLogging
import no.nav.sokos.utleggstrekk.config.PropertiesConfig
import javax.jms.Connection
import javax.jms.MessageConsumer
import javax.jms.Session
import javax.jms.TextMessage

class MqConsumer(
    private val config: PropertiesConfig.MqProperties = PropertiesConfig.MqProperties()
) {
    private lateinit var session: Session
    private lateinit var mqConsumer: MessageConsumer
    private var connected: Boolean = false

    private val logger = KotlinLogging.logger { }
    private val secureLogger = KotlinLogging.logger("secureLogger")

    init {
        connect()
    }

    private fun connect() {
        logger.info("Connecting to MQ queue ${config.replyQueueName}")
        val connection = config.connect()
        session = connection.createSession(Session.SESSION_TRANSACTED)
        val queue = (session.createQueue(config.replyQueueName) as MQQueue).apply {
            targetClient = WMQConstants.WMQ_CLIENT_NONJMS_MQ
        }
        mqConsumer = session.createConsumer(queue)
        connection.start()
        connected = true
    }

    fun receive(): String? {
        try {
            if (!connected) connect()
            return when (val message = mqConsumer.receive(500L)) {
                is TextMessage -> message.text
                else -> null
            }
        } catch (ex: Exception) {
            connected = false
            throw ex
        }
    }

    fun commit()= session.commit()

    fun rollback() = session.rollback()

    private fun PropertiesConfig.MqProperties.connect(): Connection = MQConnectionFactory().also {
        it.transportType = WMQConstants.WMQ_CM_CLIENT
        it.hostName = hostName
        it.port = port.toInt()
        it.channel = channel
        it.queueManager = queueManagerName
        it.targetClientMatching = true
        it.setBooleanProperty(JmsConstants.USER_AUTHENTICATION_MQCSP, true)
    }.createConnection(username, password)
}