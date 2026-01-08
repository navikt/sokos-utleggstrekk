package no.nav.sokos.utleggstrekk.unleash

import io.getunleash.DefaultUnleash
import io.getunleash.FakeUnleash
import io.getunleash.Unleash
import io.getunleash.util.UnleashConfig

import no.nav.sokos.utleggstrekk.config.PropertiesConfigOld
import no.nav.sokos.utleggstrekk.config.PropertiesConfigOld.Configuration
import no.nav.sokos.utleggstrekk.config.PropertiesConfigOld.UnleashProperties

open class UnleashIntegration {
    private var unleashClient: Unleash
    val unleashIsEnabled = Configuration().unleashEnabled

    // Kill switcher:
    fun isHentFraSKEEnabled(): Boolean = unleashClient.isEnabled("sokos-utleggstrekk.hent-fra-ske.enabled", unleashIsEnabled)

    fun isSendTilOSEnabled(): Boolean = unleashClient.isEnabled("sokos-utleggstrekk.send-til-os.enabled", unleashIsEnabled)

    fun isProsesserUtleggstrekkEnabled(): Boolean = unleashClient.isEnabled("sokos-utleggstrekk.prosesser-utleggstrekk.enabled", unleashIsEnabled)

    init {
        if (Configuration().profile == PropertiesConfigOld.Profile.LOCAL) {
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