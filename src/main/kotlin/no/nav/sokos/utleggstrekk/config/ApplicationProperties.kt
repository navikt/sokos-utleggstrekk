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
    val configuration: AppConfig,
) {
    val isLocal = profile == Profile.LOCAL
    val isTest = profile == Profile.TEST
}

@Serializable
data class AppConfig(
    val scheduler: SchedulerProperties = SchedulerProperties(),
    val security: SecurityProperties,
)

@Serializable
data class SchedulerProperties(
    val isActive: Boolean = false,
    val minutes: Int = 45,
)

@Serializable
data class SecurityProperties(val azure: AzureAdProperties)

@Serializable
data class AzureAdProperties(
    val clientId: String,
    val wellKnownUrl: String,
)