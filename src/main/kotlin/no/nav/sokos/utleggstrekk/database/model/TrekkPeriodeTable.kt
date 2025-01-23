package no.nav.sokos.utleggstrekk.database.model

data class TrekkPeriodeTable(
    val trekkPeriodeTableId: Int,
    val sekvensnummer: Int,
    val trekkidSke: Int,
    val trekkversjon: Int,
    val datoStart: String,
    val datoSlutt: String?,
    val trekkbelop: Double?,
    val trekkprosent: Double?
)

