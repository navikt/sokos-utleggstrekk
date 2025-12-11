package no.nav.sokos.utleggstrekk.utils

import no.nav.sokos.utleggstrekk.config.PropertiesConfig
import no.nav.sokos.utleggstrekk.database.model.BetalingsinformasjonFraSkatt

object TssIdResolver {
    val config = PropertiesConfig.SKEConfig()

    fun resolve(betalingsinformasjonFraSkatt: BetalingsinformasjonFraSkatt) =
        if (betalingsinformasjonFraSkatt.betalingsmottaker == config.skeOrgNr && betalingsinformasjonFraSkatt.kontonummer == config.skeKontoNr) {
            config.skeTSSId
        } else {
            throw IllegalArgumentException(
                "Kombinasjonen Orgnr=${betalingsinformasjonFraSkatt.betalingsmottaker} og Konto=${betalingsinformasjonFraSkatt.kontonummer} gir ingen TSSID.",
            )
        }
}