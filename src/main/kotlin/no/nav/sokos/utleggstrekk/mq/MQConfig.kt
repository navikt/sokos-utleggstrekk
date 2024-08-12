package no.nav.sokos.utleggstrekk.mq

import com.ibm.mq.constants.MQConstants
import com.ibm.msg.client.jms.JmsConstants.WMQ_PROVIDER
import com.ibm.msg.client.jms.JmsFactoryFactory
import com.ibm.msg.client.wmq.WMQConstants
import no.nav.sokos.utleggstrekk.config.PropertiesConfig
import javax.jms.ConnectionFactory

private const val UTF_8_WITH_PUA = 1208

object MQConfig {
    fun connectionFactory(properties: PropertiesConfig.MqProperties = PropertiesConfig.MqProperties()): ConnectionFactory =
        JmsFactoryFactory.getInstance(WMQ_PROVIDER).createConnectionFactory().apply {
            setIntProperty(WMQConstants.WMQ_CONNECTION_MODE, WMQConstants.WMQ_CM_CLIENT)
            setStringProperty(WMQConstants.WMQ_QUEUE_MANAGER, properties.queueManagerName)
            setStringProperty(WMQConstants.WMQ_HOST_NAME, properties.hostName)
            setStringProperty(WMQConstants.WMQ_APPLICATIONNAME, PropertiesConfig.Configuration().naisAppName)
            setIntProperty(WMQConstants.WMQ_PORT, properties.port)
            setStringProperty(WMQConstants.WMQ_CHANNEL, properties.channel)
            setIntProperty(WMQConstants.WMQ_CCSID, UTF_8_WITH_PUA)
            setBooleanProperty(WMQConstants.WMQ_TARGET_CLIENT_MATCHING, true)
            setIntProperty(WMQConstants.JMS_IBM_ENCODING, MQConstants.MQENC_NATIVE)
            setIntProperty(WMQConstants.JMS_IBM_CHARACTER_SET, UTF_8_WITH_PUA)
            setBooleanProperty(WMQConstants.USER_AUTHENTICATION_MQCSP, properties.userAuth)
            setStringProperty(WMQConstants.USERID, properties.username)
            setStringProperty(WMQConstants.PASSWORD, properties.password)
        }
}