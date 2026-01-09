package no.nav.sokos.utleggstrekk

import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.ApplicationConfigValue

object AppSettings {
    lateinit var config: ApplicationConfig
        private set

    fun load(applicationConfig: ApplicationConfig) {
        if (!::config.isInitialized) {
            config = applicationConfig
        }
    }

    fun property(name: String): ApplicationConfigValue = config.property(name)
}