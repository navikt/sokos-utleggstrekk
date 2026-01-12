package no.nav.sokos.utleggstrekk

import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.getAs

import no.nav.sokos.utleggstrekk.config.ApplicationProperties
import no.nav.sokos.utleggstrekk.config.MQProperties
import no.nav.sokos.utleggstrekk.config.MaskinportenClientConfig
import no.nav.sokos.utleggstrekk.config.PostgresConfig
import no.nav.sokos.utleggstrekk.config.SkeConfig
import no.nav.sokos.utleggstrekk.config.SlackConfig

object AppSettings {
    lateinit var config: ApplicationConfig
        private set

    val applicationProperties by lazy {
        config.property("application").getAs<ApplicationProperties>()
    }

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

    fun load(applicationConfig: ApplicationConfig) {
        if (!::config.isInitialized) {
            config = applicationConfig
        }
    }
}