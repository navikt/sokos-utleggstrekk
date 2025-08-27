package no.nav.sokos.utleggstrekk.database.model

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import kotliquery.Row

data class TrekkPeriodeTable(
    val trekkPeriodeTableId: Int,
    val sekvensnummer: Int,
    val trekkidSke: String,
    val trekkversjon: Int,
    val datoStart: String,
    val datoSlutt: String,
    val sats: Double,
    val trekkAlternativ: String,
    val tidspunktOpprettet: LocalDateTime = java.time.LocalDateTime.now().toKotlinLocalDateTime(),
    val kilde: String = "SKATTEETATEN"
) {
    constructor(row: Row) : this(
        trekkPeriodeTableId = row.int("id"),
        sekvensnummer = row.int("sekvensnummer"),
        trekkidSke = row.string("trekkid_ske"),
        trekkversjon = row.int("trekkversjon"),
        datoStart = row.string("dato_start"),
        datoSlutt = row.string("dato_slutt"),
        sats = row.double("sats"),
        trekkAlternativ = row.string("trekkalternativ"),
        kilde = row.string("kilde"),
        tidspunktOpprettet = row.localDateTime("tidspunkt_opprettet").toKotlinLocalDateTime(),
    )
}