package no.nav.sokos.utleggstrekk.database.model

import kotlinx.datetime.LocalDateTime

import kotliquery.Row

import no.nav.sokos.utleggstrekk.domene.nav.Aksjonskode

data class Varkuleklasse(
    val id: Long,
    val fraSkattID: Long,
    val status: UtleggstrekkStatus,
    val sendtOs: LocalDateTime,
    val kvitteringStatus: String,
    val aksjonskode: Aksjonskode,
    val belop: Double,
)

data class TrekkFraSkatt(
    val id: Long,
    val trekkid: String,
    val sekvensnummer: Int,
    val trekkversjon: Int,
    val opprettet: String,
    val saksnummer: String,
    val trekkpliktig: String,
    val skyldner: String,
    val trekkstatus: String,
) {
    constructor(row: Row) : this(
        id = row.long("id"),
        trekkid = row.string("trekkid"),
        sekvensnummer = row.int("sekvensnummer"),
        trekkversjon = row.int("trekkversjon"),
        opprettet = row.string("opprettet"),
        saksnummer = row.string("saksnummer"),
        trekkpliktig = row.string("trekkpliktig"),
        skyldner = row.string("skyldner"),
        trekkstatus = row.string("trekkstatus"),
    )
}

data class Periode(
    val id: Long,
    val fraSkattID: Long,
    val startdato: String,
    val sluttdato: String?,
    val trekkbeloep: Double?,
    val trekkprosent: Double?,
) {
    constructor(row: Row) : this(
        id = row.long("id"),
        fraSkattID = row.long("fraskatt_id"),
        startdato = row.string("dato_start"),
        sluttdato = row.stringOrNull("dato_slutt"),
        trekkbeloep = row.doubleOrNull("trekkbelop"),
        trekkprosent = row.doubleOrNull("trekkprosent"),
    )
}

data class BetalingsinformasjonFraSkatt(
    val id: Long,
    val fraSkattID: Long,
    val betalingsmottaker: String,
    val kidnummer: String,
    val kontonummer: String,
) {
    constructor(row: Row) : this(
        id = row.long("id"),
        fraSkattID = row.long("fraskatt_id"),
        betalingsmottaker = row.string("betalingsmottaker"),
        kidnummer = row.string("kidnummer"),
        kontonummer = row.string("kontonummer"),
    )
}