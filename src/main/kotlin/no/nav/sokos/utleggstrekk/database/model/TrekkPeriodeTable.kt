package no.nav.sokos.utleggstrekk.database.model

data class TrekkPeriodeTable(
    val trekkPeriodeTableId: Int,
    val sekvensnummer: Int,
    val trekkidSke: String,
    val trekkversjon: Int,
    val datoStart: String,
    val datoSlutt: String?,
    val sats: Double,
    val trekkAlternativ: String,
    val kilde: String = "SKATTEETATEN",
)

