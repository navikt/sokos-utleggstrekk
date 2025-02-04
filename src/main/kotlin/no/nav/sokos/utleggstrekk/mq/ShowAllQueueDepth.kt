package no.nav.sokos.utleggstrekk.mq

import com.ibm.mq.MQEnvironment
import com.ibm.mq.MQException
import com.ibm.mq.MQMessage
import com.ibm.mq.MQQueue
import com.ibm.mq.MQQueueManager
import com.ibm.mq.constants.CMQC.MQOO_INPUT_SHARED
import no.nav.sokos.utleggstrekk.config.PropertiesConfig

class ShowAllQueueDepth(
    val mqConfig: PropertiesConfig.MqProperties = PropertiesConfig.MqProperties(),
) {
    init {
        MQEnvironment.hostname = mqConfig.hostName
        MQEnvironment.port = mqConfig.port
        MQEnvironment.channel = mqConfig.channel
        MQEnvironment.userID = mqConfig.replyQueueUsername
        MQEnvironment.password = mqConfig.replyQueuePassword
    }

    fun allLocalQueueDepths() {
        val qmgr = MQQueueManager(mqConfig.queueManagerName)

        try {
            println("******Åpner kø for lesing: ${mqConfig.replyQueueName}")
            val queue: MQQueue = qmgr.accessQueue(mqConfig.replyQueueName, MQOO_INPUT_SHARED)
            println("*****${mqConfig.replyQueueName} er tigjengelig")
            var msg = MQMessage()
            queue.get(msg)
        } catch (e: MQException) {
            println("*****${mqConfig.replyQueueName} er ikke tilgjengelig -  ${e.reasonCode}")
        }

        //   "\nNavn: $queueName Dybde: $depth Kan skrive: $write"
    }
}

