package no.nav.sokos.utleggstrekk.unleash

import io.getunleash.DefaultUnleash
import io.getunleash.FakeUnleash
import io.getunleash.Unleash
import io.getunleash.util.UnleashConfig
import mu.KotlinLogging

import no.nav.sokos.utleggstrekk.config.PropertiesConfig
import no.nav.sokos.utleggstrekk.config.PropertiesConfig.applicationProperties
import no.nav.sokos.utleggstrekk.config.PropertiesConfig.unleashProperties

private val logger = KotlinLogging.logger { }

open class UnleashIntegration {
    private enum class ToggleNames(val key: String) {
        HENT_FRA_SKE("sokos-utleggstrekk.hent-fra-ske.enabled"),
        SEND_TIL_OS("sokos-utleggstrekk.send-til-os.enabled"),
        BEHANDLE_TREKK("sokos-utleggstrekk.prosesser-utleggstrekk.enabled"),
    }

    private var unleashClient: Unleash
    private val lastStates: MutableMap<String, Boolean> = mutableMapOf()

    private fun lastStateOf(toggleName: String): Boolean = lastStates.getOrPut(toggleName) { unleashProperties.enabledByDefault }

    // Kill switcher:
    fun isHentFraSKEEnabled(): Boolean = isEnabled(ToggleNames.HENT_FRA_SKE.key)

    fun isSendTilOSEnabled(): Boolean = isEnabled(ToggleNames.SEND_TIL_OS.key)

    fun isProsesserUtleggstrekkEnabled(): Boolean = isEnabled(ToggleNames.BEHANDLE_TREKK.key)

    fun isEnabled(toggleName: String): Boolean {
        val state = unleashClient.isEnabled(toggleName, unleashProperties.enabledByDefault)
        val lastState = lastStateOf(toggleName)
        if (lastState != state) {
            val message = "$toggleName has switched from $lastState to $state"
            logger.info { message }
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
        // Fetch current states
        ToggleNames.entries.map { it.key }.forEach { toggleName ->
            lastStates[toggleName] = unleashClient.isEnabled(toggleName, unleashProperties.enabledByDefault)
        }
    }
}