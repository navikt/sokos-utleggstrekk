package no.nav.sokos.utleggstrekk.database.model

import java.time.LocalDate

import kotliquery.Row

data class PeriodeTilOS(
    val id: Long = 0,
    val osTransaksjonId: Long = 0,
    val sats: Double,
    val fom: String,
    val tom: String?,
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

data class PerioderTilOS(
    val LOPM: List<PeriodeTilOS>,
    val LOPP: List<PeriodeTilOS>,
)