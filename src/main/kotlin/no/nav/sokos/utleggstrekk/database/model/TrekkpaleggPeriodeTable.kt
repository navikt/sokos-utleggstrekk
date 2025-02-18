package no.nav.sokos.utleggstrekk.database.model

data class TrekkpaleggPeriodeTable(
    val trekkpaleggPeriodeTableId: Int,
    val sekvensnummer: Int,
    val trekkidSke: String,
    val trekkversjon: Int,
    val datoStart: String,
    val datoSlutt: String?,
    val trekkbelop: Double?,
    val trekkprosent: Double?
)

