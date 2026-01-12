package no.nav.sokos.utleggstrekk.config

import kotlinx.serialization.Serializable

@Serializable
data class MQProperties(
    val hostname: String,
    val port: Int,
    val mqQueueManagerName: String,
    val mqChannelName: String,
    val queueName: String,
    val replyQueueName: String,
    val username: String,
    val password: String,
    val userAuth: Boolean,
)