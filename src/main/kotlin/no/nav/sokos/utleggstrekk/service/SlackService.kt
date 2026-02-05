package no.nav.sokos.utleggstrekk.service

import java.util.Collections

import no.nav.sokos.utleggstrekk.client.SlackClient

data class ErrorMessage(
    val type: String,
    val info: MutableList<String>,
)

class SlackService(private val slackClient: SlackClient = SlackClient()) {
    private val errorTrackingList = Collections.synchronizedList(mutableListOf<ErrorMessage>())

    // Internal accessor for testing
    internal val errorTracking: List<ErrorMessage>
        get() = synchronized(errorTrackingList) { errorTrackingList.toList() }

    /**
     * Locally cache errors to be sent to Slack
     * @param header: Error name/short description of the problem
     * @param message: More detailed description of the error
     */
    fun addError(header: String, message: String) {
        synchronized(errorTrackingList) {
            val error = errorTrackingList.find { it.type == header }
            if (error != null) {
                error.info.add(message)
            } else {
                errorTrackingList.add(ErrorMessage(header, mutableListOf(message)))
            }
        }
    }

    /**
     * Send the messages that have been cached using [addError] to Slack.
     * @param messageTitle: Header for the Slack message
     */
    suspend fun sendCachedErrors(messageTitle: String) {
        val errorsToSend =
            synchronized(errorTrackingList) {
                if (errorTrackingList.isEmpty()) return

                errorTrackingList.forEach { (type, info) ->
                    if (info.size > 5) {
                        val summary = "${info.size} av samme type feil: $type. Sjekk avstemming"
                        info.clear()
                        info.add(summary)
                    }
                }

                errorTrackingList.toList().also { errorTrackingList.clear() }
            }

        slackClient.sendMessage(messageTitle, errorsToSend)
    }

    companion object {
        val instance: SlackService by lazy { SlackService() }
    }
}