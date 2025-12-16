package no.nav.sokos.utleggstrekk.unleash

import io.getunleash.DefaultUnleash
import io.getunleash.FakeUnleash
import io.getunleash.Unleash
import io.getunleash.util.UnleashConfig

import no.nav.sokos.utleggstrekk.config.PropertiesConfig
import no.nav.sokos.utleggstrekk.config.PropertiesConfig.Configuration
import no.nav.sokos.utleggstrekk.config.PropertiesConfig.UnleashProperties

open class UnleashIntegration {
    private var unleashClient: Unleash

    // Kill switcher:
    fun isHentFraSKEEnabled(): Boolean = unleashClient.isEnabled("sokos-utleggstrekk.hent-fra-ske.enabled", true)

    fun isSendTilOSEnabled(): Boolean = unleashClient.isEnabled("sokos-utleggstrekk.send-til-os.enabled", true)

    fun isProsesserUtleggstrekkEnabled(): Boolean = unleashClient.isEnabled("sokos-utleggstrekk.prosesser-utleggstrekk.enabled", true)

    init {
        if (Configuration().profile == PropertiesConfig.Profile.LOCAL) {
            unleashClient = FakeUnleash()
        } else {
            val config: UnleashConfig =
                UnleashConfig
                    .builder()
                    .appName(Configuration().naisAppName)
                    .instanceId(Configuration().naisPodName)
                    .unleashAPI(UnleashProperties.unleashAPI + "/api/")
                    .apiKey(UnleashProperties.apiKey)
                    .environment(UnleashProperties.environment)
                    .synchronousFetchOnInitialisation(true)
                    .build()
            unleashClient = DefaultUnleash(config)
        }
    }
}
