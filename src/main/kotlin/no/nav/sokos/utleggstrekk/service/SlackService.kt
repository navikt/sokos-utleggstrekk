package no.nav.sokos.utleggstrekk.service

import no.nav.sokos.utleggstrekk.client.SlackClient

data class ErrorMessage(
    val type: String,
    val info: MutableList<String>,
)

class SlackService(private val slackClient: SlackClient = SlackClient()) {
    val errorTracking = mutableListOf<ErrorMessage>()

    /**
     * Locally cache errors to be sent to Slack
     * @param header: Error name/short description of the problem
     * @param message: More detailed description of the error
     */
    fun addError(header: String, message: String) {
        val error = errorTracking.find { it.type == header }
        if (error != null) {
            error.info.add(message)
        } else {
            errorTracking.add(ErrorMessage(header, mutableListOf(message)))
        }
    }

    /**
     * Send the messages that have been cached using [addError] to Slack.
     * @param messageTitle: Header for the Slack message
     */
    suspend fun sendCachedErrors(messageTitle: String) {
        if (errorTracking.isEmpty()) return

        errorTracking.forEach { (type, info) ->
            if (info.size > 5) {
                val summary = "${info.size} av samme type feil: $type. Sjekk avstemming"
                info.clear()
                info.add(summary)
            }
        }

        slackClient.sendMessage(messageTitle, errorTracking.toList())
        errorTracking.clear()
    }

    companion object {
        val instance: SlackService by lazy { SlackService() }
    }
}