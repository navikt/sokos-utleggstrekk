package no.nav.sokos.utleggstrekk.config

import kotlinx.serialization.Serializable

@Serializable
data class UnleashProperties(
    val unleashApi: String,
    val apiKey: String,
    val environment: String,
    val enabled: Boolean,
)