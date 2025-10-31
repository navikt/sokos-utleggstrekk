package no.nav.sokos.utleggstrekk.database.model

import java.time.LocalDate

import kotliquery.Row

import no.nav.sokos.utleggstrekk.domene.nav.TrekkAlternativ

data class PeriodeTilOS(
    val id: Long = 0,
    val osTransaksjonId: Long = 0,
    val sats: Double,
    val periodeFomDato: String,
    val periodeTomDato: String?,
) {
    constructor(row: Row) : this(
        id = row.long("id"),
        osTransaksjonId = row.long("transaksjon_os_id"),
        sats = row.double("sats"),
        periodeFomDato = row.string("periode_fom_dato"),
        periodeTomDato = row.string("periode_tom_dato"),
    )

    fun isExpired(): Boolean =
        when {
            periodeTomDato == null -> false
            else -> LocalDate.parse(periodeTomDato).isBefore(LocalDate.now())
        }
}

data class PerioderTilOS(
    val LOPM: List<PeriodeTilOS>,
    val LOPP: List<PeriodeTilOS>,
) {
    operator fun get(alternativ: TrekkAlternativ) =
        when {
            alternativ == TrekkAlternativ.LOPP -> LOPP
            alternativ == TrekkAlternativ.LOPM -> LOPM
            else -> throw NotImplementedError("Ukjent trekkalternativ $alternativ")
        }
}