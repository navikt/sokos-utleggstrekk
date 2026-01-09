package no.nav.sokos.utleggstrekk.config

import kotlinx.serialization.Serializable

@Serializable
data class SlackConfig(val url: String)