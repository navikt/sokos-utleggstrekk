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

const val REFERENCE_ID_DEFAULT = "Ingen"

data class ErrorInfo(
    val description: String,
    val referenceId: String = REFERENCE_ID_DEFAULT,
)

data class ErrorMessage(
    val type: ErrorHeader,
    val info: MutableList<ErrorInfo>,
) {
    constructor(type: ErrorHeader, info: ErrorInfo) : this(type, mutableListOf(info))
    constructor(type: ErrorHeader, description: String, referenceId: String = REFERENCE_ID_DEFAULT) : this(type, ErrorInfo(description, referenceId))

    fun referenceIDs() =
        info
            .mapNotNull {
                if (it.referenceId != REFERENCE_ID_DEFAULT) {
                    it.referenceId
                } else {
                    null
                }
            }.takeIf { it.isNotEmpty() }
            ?.distinct()
            ?.joinToString()
            ?: REFERENCE_ID_DEFAULT
}

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
    fun addError(header: ErrorHeader, message: String, referenceId: String = REFERENCE_ID_DEFAULT) {
        CoroutineScope(SupervisorJob() + Default).launch {
            addErrorSuspending(header, ErrorInfo(message, referenceId))
        }
    }

    /**
     * Locally cache errors to be sent to Slack, but suspending
     *
     * Use this if you are already in a coroutine-scope
     * @param header: Error name/short description of the problem
     * @param errorInfo: More detailed description of the error
     */
    suspend fun addErrorSuspending(header: ErrorHeader, errorInfo: ErrorInfo) {
        mutex.withLock {
            val error = errorTracking.find { it.type == header }
            if (error != null) {
                error.info.add(errorInfo)
            } else {
                errorTracking.add(ErrorMessage(header, errorInfo))
            }
        }
    }

    suspend fun addErrorSuspending(header: ErrorHeader, message: String, referenceId: String = REFERENCE_ID_DEFAULT) {
        addErrorSuspending(header, ErrorInfo(message, referenceId))
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
                    errorTracking.map { errorMessage ->
                        if (errorMessage.info.size > 5) {
                            ErrorMessage(errorMessage.type, "${errorMessage.info.size} av samme type feil. Sjekk avstemming", errorMessage.referenceIDs())
                        } else {
                            errorMessage
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
