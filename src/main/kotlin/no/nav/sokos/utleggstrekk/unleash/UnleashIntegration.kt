package no.nav.sokos.utleggstrekk.unleash

import io.getunleash.DefaultUnleash
import io.getunleash.FakeUnleash
import io.getunleash.Unleash
import io.getunleash.util.UnleashConfig
import mu.KotlinLogging

import no.nav.sokos.utleggstrekk.config.PropertiesConfig
import no.nav.sokos.utleggstrekk.config.PropertiesConfig.Configuration
import no.nav.sokos.utleggstrekk.config.PropertiesConfig.UnleashProperties
import no.nav.sokos.utleggstrekk.service.SlackService

private val logger = KotlinLogging.logger { }

open class UnleashIntegration(val slackService: SlackService) {
    val unleashIsEnabled = PropertiesConfig.Configuration().unleashEnabled
    private var unleashClient: Unleash
    private val lastStates: MutableMap<String, Boolean> = mutableMapOf()

    private fun lastStateOf(toggleName: String): Boolean = lastStates.getOrPut(toggleName) { unleashIsEnabled }

    // Kill switcher:
    fun isHentFraSKEEnabled(): Boolean = isEnabled("sokos-utleggstrekk.hent-fra-ske.enabled")

    fun isSendTilOSEnabled(): Boolean = isEnabled("sokos-utleggstrekk.send-til-os.enabled")

    fun isProsesserUtleggstrekkEnabled(): Boolean = isEnabled("sokos-utleggstrekk.prosesser-utleggstrekk.enabled")

    fun isEnabled(toggleName: String): Boolean {
        val state = unleashClient.isEnabled(toggleName, unleashIsEnabled)
        val lastState = lastStateOf(toggleName)
        if (lastState != state) {
            val message = "$toggleName has switched from $lastState to $state"
            logger.info { message }
            slackService.addError("Feature toggled", message)
            lastStates[toggleName] = state
        }
        return state
    }

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
