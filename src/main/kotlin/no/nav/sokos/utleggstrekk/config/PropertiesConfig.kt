package no.nav.sokos.utleggstrekk.config

import kotlinx.serialization.Serializable

import com.nimbusds.jose.jwk.RSAKey
import com.typesafe.config.ConfigFactory
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.HoconApplicationConfig
import io.ktor.server.config.getAs
import io.ktor.server.config.withFallback

object PropertiesConfig {
    lateinit var config: ApplicationConfig
        private set

    val applicationProperties by lazy {
        config.property("application").getAs<ApplicationProperties>()
    }

    val isLocal: Boolean
        get() = applicationProperties.isLocal

    val isTest: Boolean
        get() = applicationProperties.isTest

    val skeConfig by lazy {
        config.property("skeConfig").getAs<SkeConfig>()
    }

    val maskinportenClientConfig by lazy {
        config.property("maskinportenClientConfig").getAs<MaskinportenClientConfig>()
    }

    val mqProperties by lazy {
        config.property("mqProperties").getAs<MQProperties>()
    }

    val postgresConfig by lazy {
        config.property("postgresConfig").getAs<PostgresConfig>()
    }

    val slackConfig by lazy {
        config.property("slackConfig").getAs<SlackConfig>()
    }

    val unleashProperties by lazy {
        config.property("unleashProperties").getAs<UnleashProperties>()
    }

    fun load(applicationConfig: ApplicationConfig) {
        if (!::config.isInitialized) {
            config = applicationConfig
        }
    }
}

fun ApplicationConfig.mergeWithEnv(): ApplicationConfig {
    val hoconConfig = HoconApplicationConfig(ConfigFactory.load())
    val environment =
        (System.getenv("CLUSTER_NAME") ?: System.getProperty("CLUSTER_NAME"))
            ?.lowercase()
            ?.substringBefore("-")
            ?: propertyOrNull("ktor.environment")?.getString()
            ?: "local"
    val environmentConfig = ApplicationConfig("application-$environment.conf")
    return this overriding environmentConfig overriding hoconConfig
}

infix fun ApplicationConfig.overriding(other: ApplicationConfig): ApplicationConfig = this.withFallback(other)

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

@Serializable
data class SkeConfig(
    val skeRestUrl: String,
    val skeOrgNr: String,
    val skeKontoNr: String,
    val skeTSSId: String,
)

@Serializable
data class MaskinportenClientConfig(
    val clientId: String,
    val wellKnownUrl: String,
    val scopes: String,
    val rsaKeyString: String,
    val systemBrukerClaim: String,
) {
    val rsaKey: RSAKey? by lazy {
        RSAKey.parse(rsaKeyString)
    }
}

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

@Serializable
data class PostgresConfig(
    val host: String,
    val port: String,
    val username: String,
    val password: String,
    val jdbcUrl: String,
    val user: String,
    val name: String,
)

@Serializable
data class SlackConfig(val url: String)

@Serializable
data class UnleashProperties(
    val unleashApi: String = "",
    val apiKey: String = "",
    val environment: String = "",
    val enabled: Boolean,
)