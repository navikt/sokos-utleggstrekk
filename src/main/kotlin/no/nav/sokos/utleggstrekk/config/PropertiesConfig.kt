package no.nav.sokos.utleggstrekk.config

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