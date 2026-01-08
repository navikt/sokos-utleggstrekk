package no.nav.sokos.utleggstrekk.config

import kotlinx.serialization.Serializable

import com.typesafe.config.ConfigFactory
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.HoconApplicationConfig
import io.ktor.server.config.withFallback

enum class Profile {
    LOCAL,
    TEST,
    PROD,
}

@Serializable
data class ApplicationProperties(
    val profile: Profile,
    val appName: String,
    val namespace: String,
    val configuration: AppConfig,
) {
    // TODO: use correct isLocal
//    val isLocal = profile == Profile.LOCAL
    val isLocal = profile == Profile.TEST
}

@Serializable
data class AppConfig(
    val scheduler: SchedulerProperties = SchedulerProperties(),
    val security: SecurityProperties,
)

@Serializable
data class SchedulerProperties(
    val isActive: Boolean = false,
    val minutes: Int = 55,
)

@Serializable
data class SecurityProperties(val azure: AzureAdProperties)

@Serializable
data class AzureAdProperties(
    val clientId: String,
    val wellKnownUrl: String,
)

fun ApplicationConfig.mergeWithEnv(): ApplicationConfig {
    val hoconConfig = HoconApplicationConfig(ConfigFactory.load())
    val environment =
        (System.getenv("CLUSTER_NAME") ?: System.getProperty("CLUSTER_NAME"))
            ?.lowercase()
            ?.substringBefore("-")
            ?: propertyOrNull("ktor.environment")?.getString()
            ?: "test"
    val environmentConfig = ApplicationConfig("application-$environment.conf")
    return this overriding environmentConfig overriding hoconConfig
}

infix fun ApplicationConfig.overriding(other: ApplicationConfig): ApplicationConfig = this.withFallback(other)