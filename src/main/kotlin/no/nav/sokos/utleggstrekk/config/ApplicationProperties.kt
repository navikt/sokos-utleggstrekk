package no.nav.sokos.utleggstrekk.config

import kotlinx.serialization.Serializable

enum class Profile {
    LOCAL,
    DEV,
    TEST,
    PROD,
}

@Serializable
data class ApplicationProperties(
    val profile: Profile,
    val appName: String,
    val namespace: String,
    val naisPodName: String,
    val scheduler: SchedulerProperties = SchedulerProperties(),
) {
    val isLocal = profile == Profile.LOCAL
    val isTest = profile == Profile.TEST
}

@Serializable
data class SchedulerProperties(
    val isActive: Boolean = false,
    val minutes: Int = 45,
)