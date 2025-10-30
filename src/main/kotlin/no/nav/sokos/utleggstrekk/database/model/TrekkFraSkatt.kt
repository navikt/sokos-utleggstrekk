package no.nav.sokos.utleggstrekk.database.model

import kotliquery.Row

import no.nav.sokos.utleggstrekk.domene.nav.TrekkAlternativ
import no.nav.sokos.utleggstrekk.domene.nav.TrekkAlternativ.LOPM

const val SOKOS_KILDE = "SOKOS-UTLEGGSTREKK"
const val SKATT_KILDE = "SKATTEETATEN"

data class FraSkattStatus(
    val id: Long,
    val fraSkattID: Long,
    val status: SkattTrekkStatus,
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

data class PeriodeFraSkatt(
    val id: Long,
    val fraSkattID: Long,
    val trekkIdSke: String,
    val startdato: String,
    val sluttdato: String?,
    val trekkbeloep: Double?,
    val trekkprosent: Double?,
) {
    constructor(row: Row) : this(
        id = row.long("id"),
        fraSkattID = row.long("fraskatt_id"),
        trekkIdSke = row.string("trekk_id_ske"),
        startdato = row.string("dato_start"),
        sluttdato = row.stringOrNull("dato_slutt"),
        trekkbeloep = row.doubleOrNull("trekkbelop"),
        trekkprosent = row.doubleOrNull("trekkprosent"),
    )

    fun satsFor(alternativ: TrekkAlternativ): Double =
        if (alternativ == TrekkAlternativ.LOPM) {
            trekkbeloep ?: 0.0
        } else {
            trekkprosent ?: 0.0
        }

    fun kildeFor(alternativ: TrekkAlternativ): String =
        if (alternativ == LOPM && trekkbeloep != null) {
            SKATT_KILDE
        } else {
            SOKOS_KILDE
        }

    fun trekkAlternativ(): TrekkAlternativ =
        when {
            trekkbeloep == null -> TrekkAlternativ.LOPP
            else -> TrekkAlternativ.LOPM
        }
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

enum class SkattTrekkStatus {
    MOTTATT,
    BEHANDLET,
}