package no.nav.sokos.utleggstrekk.service

import no.nav.sokos.utleggstrekk.client.SlackClient

data class ErrorMessage(
    val type: String,
    val info: MutableList<String>,
)

class SlackService(private val slackClient: SlackClient = SlackClient()) {
    val errorTracking = mutableListOf<ErrorMessage>()

    fun addError(header: String, message: String) {
        val error = errorTracking.find { it.type == header }
        if (error != null) {
            error.info.add(message)
        } else {
            errorTracking.add(ErrorMessage(header, mutableListOf(message)))
        }
    }

    suspend fun sendErrors(messageTitle: String) {
        if (errorTracking.isEmpty()) return

        errorTracking.map { (type, info) ->
            if (info.size > 5) {
                val summary = "${info.size} av samme type feil: $type. Sjekk avstemming"
                info.clear()
                info.add(summary)
            }
        }

        slackClient.sendMessage(messageTitle, errorTracking.toList())
        errorTracking.clear()
    }
}