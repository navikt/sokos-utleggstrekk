package no.nav.sokos.utleggstrekk.mq

import com.ibm.mq.jms.MQConnectionFactory
import com.ibm.mq.jms.MQQueue
import com.ibm.msg.client.jms.JmsConstants
import com.ibm.msg.client.wmq.WMQConstants
import mu.KotlinLogging
import no.nav.sokos.utleggstrekk.config.PropertiesConfig
import javax.jms.Connection
import javax.jms.MessageProducer
import javax.jms.Session

private val logger = KotlinLogging.logger {}

class MqProducer(
    private val config: PropertiesConfig.MqProperties,
) {
    private lateinit var session: Session
    private lateinit var messageProducer: MessageProducer
    private var connected: Boolean = false

    private val logger = KotlinLogging.logger { }

    init {
        connect()
    }

    private fun connect() {
        logger.info("Connecting to MQ queue ${config.queue}")
        val connection = config.connect()
        session = connection.createSession(Session.SESSION_TRANSACTED)
        val queue =
            (session.createQueue(config.queue) as MQQueue).apply {
                targetClient = WMQConstants.WMQ_CLIENT_NONJMS_MQ
            }
        messageProducer = session.createProducer(queue)
        connection.start()
        connected = true
    }

    fun send(message: String) {
        try {
            if (!connected) connect()
            messageProducer.send(session.createTextMessage(message))
        } catch (ex: Exception) {
            logger.error("Feilet ved sending via MQ til OS")
            connected = false
            throw ex
        }
    }

    fun commit() = session.commit()

    fun rollback() = session.rollback()

    private fun PropertiesConfig.MqProperties.connect(): Connection =
        MQConnectionFactory().also {
            it.transportType = WMQConstants.WMQ_CM_CLIENT
            it.hostName = host
            it.port = port.toInt()
            it.channel = channel
            it.queueManager = qmgrName
            it.targetClientMatching = true
            it.setBooleanProperty(JmsConstants.USER_AUTHENTICATION_MQCSP, true)
        }.createConnection(username, password)
}