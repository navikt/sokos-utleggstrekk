package no.nav.sokos.utleggstrekk.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

import no.nav.sokos.utleggstrekk.config.PropertiesConfig
import no.nav.sokos.utleggstrekk.database.model.BetalingsinformasjonFraSkatt
import no.nav.sokos.utleggstrekk.service.SlackService

object TssIdResolver {
    val config = PropertiesConfig.SKEConfig()

    fun resolve(betalingsinformasjonFraSkatt: BetalingsinformasjonFraSkatt) =
        if (betalingsinformasjonFraSkatt.betalingsmottaker == config.skeOrgNr && betalingsinformasjonFraSkatt.kontonummer == config.skeKontoNr) {
            config.skeTSSId
        } else {
            val message = "Kombinasjonen Orgnr=${betalingsinformasjonFraSkatt.betalingsmottaker} og Konto=${betalingsinformasjonFraSkatt.kontonummer} gir ingen TSSID."
            sendAlarm(message)
            throw IllegalArgumentException(message)
        }

    private fun sendAlarm(message: String) {
        SlackService.instance.addError("IllegalArgumentException", message)
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        appScope.launch {
            SlackService.instance.sendCachedErrors("TssIdResolver failed")
        }
    }
}