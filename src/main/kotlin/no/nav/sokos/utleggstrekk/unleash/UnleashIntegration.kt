package no.nav.sokos.utleggstrekk.unleash

import io.getunleash.DefaultUnleash
import io.getunleash.FakeUnleash
import io.getunleash.Unleash
import io.getunleash.util.UnleashConfig
import mu.KotlinLogging

import no.nav.sokos.utleggstrekk.config.PropertiesConfig
import no.nav.sokos.utleggstrekk.config.PropertiesConfig.applicationProperties
import no.nav.sokos.utleggstrekk.config.PropertiesConfig.unleashProperties
import no.nav.sokos.utleggstrekk.service.SlackService

private val logger = KotlinLogging.logger { }

open class UnleashIntegration(val slackService: SlackService) {
    private var unleashClient: Unleash
    private val lastStates: MutableMap<String, Boolean> = mutableMapOf()

    private fun lastStateOf(toggleName: String): Boolean = lastStates.getOrPut(toggleName) { unleashProperties.enabledByDefault }

    // Kill switcher:
    fun isHentFraSKEEnabled(): Boolean = isEnabled("sokos-utleggstrekk.hent-fra-ske.enabled")

    fun isSendTilOSEnabled(): Boolean = isEnabled("sokos-utleggstrekk.send-til-os.enabled")

    fun isProsesserUtleggstrekkEnabled(): Boolean = isEnabled("sokos-utleggstrekk.prosesser-utleggstrekk.enabled")

    fun isEnabled(toggleName: String): Boolean {
        val state = unleashClient.isEnabled(toggleName, unleashProperties.enabledByDefault)
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