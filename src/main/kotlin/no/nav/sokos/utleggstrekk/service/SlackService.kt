package no.nav.sokos.utleggstrekk.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

import no.nav.sokos.utleggstrekk.client.SlackClient
import no.nav.sokos.utleggstrekk.domene.nav.ErrorCategory
import no.nav.sokos.utleggstrekk.domene.nav.ErrorHeader

data class ErrorMessage(
    val type: ErrorHeader,
    val info: MutableList<String>,
)

class SlackService(private val slackClient: SlackClient = SlackClient()) {
    private val mutex = Mutex()
    private val errorTracking = mutableListOf<ErrorMessage>()

    fun errorTracking() = errorTracking.toList()

    /**
     * Locally cache errors to be sent to Slack
     *
     * Launches a new coroutine-scope, so you may not be guaranteed that
     * the message is registered before you get control back
     *
     * Use [addErrorSuspending] if it is important to have the message
     * registered before control returns
     * @param header: Error name/short description of the problem
     * @param message: More detailed description of the error
     */
    fun addError(header: ErrorHeader, message: String) {
        CoroutineScope(SupervisorJob() + Default).launch {
            addErrorSuspending(header, message)
        }
    }

    /**
     * Locally cache errors to be sent to Slack, but suspending
     *
     * Use this if you are already in a coroutine-scope
     * @param header: Error name/short description of the problem
     * @param message: More detailed description of the error
     */
    suspend fun addErrorSuspending(header: ErrorHeader, message: String) {
        mutex.withLock {
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
    suspend fun sendCachedErrors(messageTitle: ErrorCategory) {
        val errorsToSend =
            mutex.withLock {
                if (errorTracking.isEmpty()) return

                val preparedErrors =
                    errorTracking.map { (type, info) ->
                        if (info.size > 5) {
                            ErrorMessage(type, mutableListOf("${info.size} av samme type feil: $type. Sjekk avstemming"))
                        } else {
                            ErrorMessage(type, info.toMutableList())
                        }
                    }

                errorTracking.clear()
                preparedErrors
            }

        try {
            slackClient.sendMessage(messageTitle.value, errorsToSend)
        } catch (exception: Exception) {
            errorsToSend.forEach { (type, info) ->
                info.forEach { addErrorSuspending(type, it) }
            }
            throw exception
        }
    }

    companion object {
        val instance: SlackService by lazy { SlackService() }
    }
}
