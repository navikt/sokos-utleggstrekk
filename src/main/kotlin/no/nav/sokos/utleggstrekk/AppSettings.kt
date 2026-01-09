package no.nav.sokos.utleggstrekk

import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.getAs

import no.nav.sokos.utleggstrekk.config.ApplicationProperties
import no.nav.sokos.utleggstrekk.config.SkeConfig
import no.nav.sokos.utleggstrekk.config.SlackConfig

object AppSettings {
    lateinit var config: ApplicationConfig
        private set

    val applicationProperties by lazy {
        config.property("application").getAs<ApplicationProperties>()
    }

    val slackConfig by lazy {
        config.property("slackConfig").getAs<SlackConfig>()
    }

    val skeConfig by lazy {
        config.property("skeConfig").getAs<SkeConfig>()
    }

    fun load(applicationConfig: ApplicationConfig) {
        if (!::config.isInitialized) {
            config = applicationConfig
        }
    }
}