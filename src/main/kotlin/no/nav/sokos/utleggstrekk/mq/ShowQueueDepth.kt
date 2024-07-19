package no.nav.sokos.utleggstrekk.mq

import com.ibm.mq.MQEnvironment
import com.ibm.mq.MQQueueManager
import com.ibm.mq.constants.CMQC.MQCA_Q_NAME
import com.ibm.mq.constants.CMQC.MQIA_CURRENT_Q_DEPTH
import com.ibm.mq.constants.MQConstants
import com.ibm.mq.headers.pcf.PCFMessage
import com.ibm.mq.headers.pcf.PCFMessageAgent
import java.lang.String
import java.util.regex.Pattern
import kotlin.also


class ShowAllQueueDepth() {
    init {
        MQEnvironment.hostname = "10.53.17.126"
        MQEnvironment.port = 1413
        MQEnvironment.channel = "HERMES.SVRCONN"
        MQEnvironment.userID = "srvos-eskatt"
        MQEnvironment.password = "YVqijM6XK94l4Uav4MGOtt"
    }

    fun allLocalQueueDepths():List<kotlin.String> {
        val qmgr = MQQueueManager("MQLS01")
        val pcfCmd = PCFMessage(MQConstants.MQCMD_INQUIRE_Q)
        val agent = PCFMessageAgent(qmgr)

        pcfCmd.addParameter(MQConstants.MQCA_Q_NAME, "*")
        pcfCmd.addParameter(MQConstants.MQIA_Q_TYPE, MQConstants.MQQT_LOCAL)
        pcfCmd.addFilterParameter(MQConstants.MQIA_CURRENT_Q_DEPTH, MQConstants.MQCFOP_GREATER, 0)

        val pcfResponse = agent.send(pcfCmd).asList()


        return pcfResponse.filterNotNull()
            .filter { !Pattern.matches("^SYSTEM.*$", String.valueOf(it.getParameterValue(MQCA_Q_NAME))) }
            .filter { !Pattern.matches("^AMK.*$", String.valueOf(it.getParameterValue(MQCA_Q_NAME))) }
            .filter { !Pattern.matches("^AMQ.*$", String.valueOf(it.getParameterValue(MQCA_Q_NAME))) }
            .map { pcfMessage ->
                String.valueOf("KøNavn: ${pcfMessage.getParameterValue(MQCA_Q_NAME)}, Kødybde: ${pcfMessage.getParameterValue(MQIA_CURRENT_Q_DEPTH)}\n").also(::print)
            }

    }
}