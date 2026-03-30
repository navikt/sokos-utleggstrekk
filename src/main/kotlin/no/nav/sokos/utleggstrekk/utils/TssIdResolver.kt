package no.nav.sokos.utleggstrekk.utils

import no.nav.sokos.utleggstrekk.config.PropertiesConfig
import no.nav.sokos.utleggstrekk.database.model.BetalingsinformasjonFraSkatt
import no.nav.sokos.utleggstrekk.service.SlackService

object TssIdResolver {
    val config = PropertiesConfig.skeConfig

    fun resolve(betalingsinformasjonFraSkatt: BetalingsinformasjonFraSkatt) =
        if (betalingsinformasjonFraSkatt.betalingsmottaker == config.skeOrgNr && betalingsinformasjonFraSkatt.kontonummer == config.skeKontoNr) {
            config.skeTSSId
        } else {
            val message = "Kombinasjonen Orgnr=${betalingsinformasjonFraSkatt.betalingsmottaker} og Konto=${betalingsinformasjonFraSkatt.kontonummer} gir ingen TSSID."
            sendAlarm(message)
            throw IllegalArgumentException(message)
        }

    private fun sendAlarm(message: String) {
        // Only cache the error here. Dispatching a new CoroutineScope just to send immediately
        // creates an unscoped coroutine that may not complete before shutdown. The cached error
        // will be flushed by the next sendCachedErrors() call in the calling schedule loop.
        SlackService.instance.addError("IllegalArgumentException", message)
    }
}
