package no.nav.sokos.utleggstrekk.mq

import com.ibm.mq.MQEnvironment
import com.ibm.mq.MQException
import com.ibm.mq.MQGetMessageOptions
import com.ibm.mq.MQMessage
import com.ibm.mq.MQQueue
import com.ibm.mq.MQQueueManager
import com.ibm.mq.constants.CMQC
import no.nav.sokos.utleggstrekk.config.PropertiesConfig

class MqConsumer(
    val mqConfig: PropertiesConfig.MqProperties = PropertiesConfig.MqProperties(),
) {
    init {
        MQEnvironment.hostname = mqConfig.hostName.also { println(it) }
        MQEnvironment.port = mqConfig.port.also { println(it) }
        MQEnvironment.channel = mqConfig.channel.also { println(it) }
        MQEnvironment.userID = mqConfig.replyQueueUsername.also { println(it) }
        MQEnvironment.password = mqConfig.replyQueuePassword.also { println(it) }
    }

    fun getKvitteringFromMQ(): List<String> {
        val qmgr = MQQueueManager(mqConfig.queueManagerName.also { println(it) })

        val meldinger = mutableListOf<String>()
        try {
            println("******Åpner kø for lesing: ${mqConfig.replyQueueName}")
            val queue: MQQueue = qmgr.accessQueue(mqConfig.replyQueueName, CMQC.MQOO_INPUT_SHARED)
            println("*****${mqConfig.replyQueueName} er tigjengelig")
            var msg = MQMessage()
            val gmo: MQGetMessageOptions = MQGetMessageOptions().apply {
                this.options = CMQC.MQGMO_WAIT +
                        CMQC.MQGMO_CONVERT +
                        CMQC.MQGMO_COMPLETE_MSG
                this.waitInterval = 300_000
            }

            msg.format = CMQC.MQFMT_STRING
            println("\n GET")
            msg.resizeBuffer(10000000)
            while (true) {
                queue.get(msg, gmo)
                val m = ByteArray(msg.getDataLength())
                msg.readFully(m)
                println("message: " + String(m))
                meldinger.add(m.toString())
            }
            return meldinger
        } catch (e: MQException) {
            if (e.reasonCode != 2033)
                println("*****Meldinger på ${mqConfig.replyQueueName} er ikke tilgjengelig -  ${e.reasonCode}")
            return meldinger
        }

    }
}