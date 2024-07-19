package no.nav.sokos.utleggstrekk.mq

import com.ibm.mq.MQEnvironment
import com.ibm.mq.MQQueueManager
import com.ibm.mq.constants.MQConstants
import com.ibm.mq.headers.pcf.PCFMessage
import com.ibm.mq.headers.pcf.PCFMessageAgent

class ShowAllQueueDepth() {
    init {
        MQEnvironment.hostname = "10.53.17.126"
        MQEnvironment.port = 1413
        MQEnvironment.channel = "HERMES.SVRCONN"
        MQEnvironment.userID = "srvos-eskatt"
        MQEnvironment.password = "YVqijM6XK94l4Uav4MGOtt"
    }

    fun allLocalQueueDepths():Array<PCFMessage> {
        val qmgr = MQQueueManager("MQLS01")
        val pcfCmd = PCFMessage(MQConstants.MQCMD_INQUIRE_Q)
        val agent = PCFMessageAgent()

        pcfCmd.addParameter(MQConstants.MQCA_Q_NAME, "*")
        pcfCmd.addParameter(MQConstants.MQIA_Q_TYPE, MQConstants.MQQT_LOCAL)
        pcfCmd.addFilterParameter(MQConstants.MQIA_CURRENT_Q_DEPTH, MQConstants.MQCFOP_GREATER, 0)

        val pcfResponse = agent.send(pcfCmd)


        for (index in pcfResponse.indices) {
            val response = pcfResponse[index]

            println(
                response.getParameterValue(MQConstants.MQCA_Q_NAME).toString() + "     "
                        + response.getParameterValue(MQConstants.MQIA_CURRENT_Q_DEPTH)
            )
        }

        return pcfResponse
    }
}