package no.nav.sokos.utleggstrekk.database.model

import java.time.LocalDate

import kotliquery.Row

data class PeriodeTilOS(
    val id: Long,
    val osTransaksjonId: Long,
    val sats: Double,
    val fom: String,
    val tom: String,
) {
    constructor(row: Row) : this(
        id = row.long("id"),
        osTransaksjonId = row.long("transaksjons_os_id"),
        sats = row.double("sats"),
        fom = row.string("fom"),
        tom = row.string("tom"),
    )

    fun isExpired(): Boolean =
        when {
            tom == null -> false
            else -> LocalDate.parse(tom).isBefore(LocalDate.now())
        }
}