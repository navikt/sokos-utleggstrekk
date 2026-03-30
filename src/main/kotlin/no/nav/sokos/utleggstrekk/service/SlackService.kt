package no.nav.sokos.utleggstrekk.service

import java.util.Collections

import no.nav.sokos.utleggstrekk.client.SlackClient

data class ErrorMessage(
    val type: String,
    val info: MutableList<String>,
)

class SlackService(private val slackClient: SlackClient = SlackClient()) {
    // Thread-safe list: addError may be called concurrently from the JMS listener thread and
    // the scheduler coroutines. Using a synchronized list prevents ConcurrentModificationException.
    private val errorTracking: MutableList<ErrorMessage> = Collections.synchronizedList(mutableListOf())

    /**
     * Locally cache errors to be sent to Slack
     * @param header: Error name/short description of the problem
     * @param message: More detailed description of the error
     */
    fun addError(header: String, message: String) {
        synchronized(errorTracking) {
            val error = errorTracking.find { it.type == header }
            if (error != null) {
                error.info.add(message)
            } else {
                errorTracking.add(ErrorMessage(header, mutableListOf(message)))
            }
        }
    }

    /**
     * Send the messages that have been cached using [addError] to Slack.
     * @param messageTitle: Header for the Slack message
     */
    suspend fun sendCachedErrors(messageTitle: String) {
        // Snapshot under lock so that concurrent addError calls do not race with the send.
        val snapshot =
            synchronized(errorTracking) {
                if (errorTracking.isEmpty()) return
                errorTracking.toList().also { errorTracking.clear() }
            }

        snapshot.forEach { (type, info) ->
            if (info.size > 5) {
                val summary = "${info.size} av samme type feil: $type. Sjekk avstemming"
                info.clear()
                info.add(summary)
            }
        }

        slackClient.sendMessage(messageTitle, snapshot)
    }

    companion object {
        val instance: SlackService by lazy { SlackService() }
    }
}
