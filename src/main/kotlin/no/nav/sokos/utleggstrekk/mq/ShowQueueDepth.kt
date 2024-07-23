package no.nav.sokos.utleggstrekk.mq

import com.ibm.mq.MQEnvironment
import com.ibm.mq.MQException
import com.ibm.mq.MQQueue
import com.ibm.mq.MQQueueManager
import com.ibm.mq.constants.CMQC.MQCA_Q_NAME
import com.ibm.mq.constants.CMQC.MQIA_CURRENT_Q_DEPTH
import com.ibm.mq.constants.CMQC.MQOO_OUTPUT
import com.ibm.mq.constants.MQConstants
import com.ibm.mq.headers.pcf.PCFMessage
import com.ibm.mq.headers.pcf.PCFMessageAgent
import no.nav.sokos.utleggstrekk.config.PropertiesConfig
import java.util.regex.Pattern


class ShowAllQueueDepth(
    val mqConfig: PropertiesConfig.MqProperties = PropertiesConfig.MqProperties()
) {
    init {
        MQEnvironment.hostname = mqConfig.host
        MQEnvironment.port = mqConfig.host.toInt()
        MQEnvironment.channel = mqConfig.channel
        MQEnvironment.userID = mqConfig.username
        MQEnvironment.password = mqConfig.password
    }

    fun allLocalQueueDepths(namePart:String  = ""):List<String> {
        val qmgr = MQQueueManager(mqConfig.qmgrName)
        val pcfCmd = PCFMessage(MQConstants.MQCMD_INQUIRE_Q)
        val agent = PCFMessageAgent(qmgr)

        pcfCmd.addParameter(MQConstants.MQCA_Q_NAME, "*")
        pcfCmd.addParameter(MQConstants.MQIA_Q_TYPE, MQConstants.MQQT_LOCAL)
        pcfCmd.addFilterParameter(MQConstants.MQIA_CURRENT_Q_DEPTH, MQConstants.MQCFOP_GREATER, 0)

        val pcfResponse = agent.send(pcfCmd).asList()


        return pcfResponse.filterNotNull().let {
            val respList = if (namePart.isNotBlank()) {
                it.filter { java.lang.String.valueOf(it.getParameterValue(MQCA_Q_NAME)).contains(namePart)}
            } else { it }
            respList
//                .filter { Pattern.matches("^.*_BOQ.*", java.lang.String.valueOf( it.getParameterValue(MQCA_Q_NAME))) }
            .filter { !Pattern.matches("^SYSTEM.*$", (it.getParameterValue(MQCA_Q_NAME) as String)) }
            .filter { !Pattern.matches("^AMK.*$", (it.getParameterValue(MQCA_Q_NAME) as String)) }
            .filter { !Pattern.matches("^AMQ.*$", (it.getParameterValue(MQCA_Q_NAME) as String)) }
            .map { pcfm ->
                val queueName = pcfm.getParameterValue(MQCA_Q_NAME).toString()
                val depth = pcfm.getParameterValue(MQIA_CURRENT_Q_DEPTH).toString()
                var write = false
                try {
                    val queue: MQQueue = qmgr.accessQueue(queueName, MQOO_OUTPUT)
                    write = true
                    println("$queueName kan lese = $write")
                } catch (e: MQException) {
                    println("$queueName kan lese = $write -  ${e.reasonCode}")
                }

                "\nNavn: $queueName Dybde: $depth Kan skrive: $write"
            }
        }
    }
}