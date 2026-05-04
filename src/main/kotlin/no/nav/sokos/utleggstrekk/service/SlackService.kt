package no.nav.sokos.utleggstrekk.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

import no.nav.sokos.utleggstrekk.client.SlackClient

data class ErrorMessage(
    val type: String,
    val info: MutableList<String>,
)

class SlackService(private val slackClient: SlackClient = SlackClient()) {
    private val mutex = Mutex()
    val errorTracking = mutableListOf<ErrorMessage>()

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
    fun addError(header: String, message: String) {
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
    suspend fun addErrorSuspending(header: String, message: String) {
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
    suspend fun sendCachedErrors(messageTitle: String) {
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
            slackClient.sendMessage(messageTitle, errorsToSend)
        } catch (exception: Exception) {
            // Requeue the batch if Slack sending fails.
            mutex.withLock {
                errorsToSend.forEach { (type, info) ->
                    val existingError = errorTracking.find { it.type == type }
                    if (existingError != null) {
                        existingError.info.addAll(info)
                    } else {
                        errorTracking.add(ErrorMessage(type, info.toMutableList()))
                    }
                }
            }
            throw exception
        }
    }

    companion object {
        val instance: SlackService by lazy { SlackService() }
    }
}