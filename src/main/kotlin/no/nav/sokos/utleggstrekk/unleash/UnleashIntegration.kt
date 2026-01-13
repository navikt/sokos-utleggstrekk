package no.nav.sokos.utleggstrekk.unleash

import io.getunleash.DefaultUnleash
import io.getunleash.FakeUnleash
import io.getunleash.Unleash
import io.getunleash.util.UnleashConfig

import no.nav.sokos.utleggstrekk.config.PropertiesConfig
import no.nav.sokos.utleggstrekk.config.PropertiesConfig.applicationProperties
import no.nav.sokos.utleggstrekk.config.PropertiesConfig.unleashProperties

open class UnleashIntegration {
    private var unleashClient: Unleash
    val unleashIsEnabled = unleashProperties.enabled

    // Kill switcher:
    fun isHentFraSKEEnabled(): Boolean = unleashClient.isEnabled("sokos-utleggstrekk.hent-fra-ske.enabled", unleashIsEnabled)

    fun isSendTilOSEnabled(): Boolean = unleashClient.isEnabled("sokos-utleggstrekk.send-til-os.enabled", unleashIsEnabled)

    fun isProsesserUtleggstrekkEnabled(): Boolean = unleashClient.isEnabled("sokos-utleggstrekk.prosesser-utleggstrekk.enabled", unleashIsEnabled)

    init {
        if (PropertiesConfig.isLocal || PropertiesConfig.isTest) {
            unleashClient = FakeUnleash()
        } else {
            val config: UnleashConfig =
                UnleashConfig
                    .builder()
                    .appName(applicationProperties.appName)
                    .instanceId(applicationProperties.naisPodName)
                    .unleashAPI(unleashProperties.unleashApi + "/api/")
                    .apiKey(unleashProperties.apiKey)
                    .environment(unleashProperties.environment)
                    .synchronousFetchOnInitialisation(true)
                    .build()
            unleashClient = DefaultUnleash(config)
        }
    }
}