package no.nav.sokos.utleggstrekk.service

import no.nav.sokos.utleggstrekk.client.SlackClient

data class ErrorMessages(
    val type: String,
    val info: List<String>,
)

class SlackService(private val slackClient: SlackClient = SlackClient()) {
    suspend fun sendError(header: String, vararg errorMessages: ErrorMessages) {
        slackClient.sendMessage(
            header,
            errorMessages.associate { (type, info) -> type to info },
        )
    }
}