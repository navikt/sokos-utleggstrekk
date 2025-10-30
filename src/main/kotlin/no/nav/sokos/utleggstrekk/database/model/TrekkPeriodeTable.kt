package no.nav.sokos.utleggstrekk.database.model

import java.time.LocalDateTime

import kotliquery.Row

import no.nav.sokos.utleggstrekk.domene.nav.TrekkAlternativ

data class TrekkPeriodeTable(
    val trekkPeriodeTableId: Int,
    val trekkidSke: String,
    val trekkversjon: Int,
    val datoStart: String,
    val datoSlutt: String?,
    val sats: Double,
    val trekkAlternativ: TrekkAlternativ,
    val tidspunktOpprettet: LocalDateTime = LocalDateTime.now(),
    val kilde: String,
    val status: PeriodeStatus = PeriodeStatus.IKKE_SENDT,
    val transaksjonOSId: Long = 0L,
) {
    constructor(row: Row) : this(
        trekkPeriodeTableId = row.int("id"),
        trekkidSke = row.string("trekkid_ske"),
        trekkversjon = row.int("trekkversjon"),
        datoStart = row.string("dato_start"),
        datoSlutt = row.string("dato_slutt"),
        sats = row.double("sats"),
        trekkAlternativ = TrekkAlternativ.valueOf(row.string("trekkalternativ")),
        tidspunktOpprettet = row.localDateTime("tidspunkt_opprettet"),
        kilde = row.string("kilde"),
        status = PeriodeStatus.valueOf(row.string("status").uppercase()),
        transaksjonOSId = row.long("transaksjons_os_id"),
    )

    fun toTrekkDokumentPeriode() =
        no.nav.sokos.utleggstrekk.domene.nav.Periode(
            periodeFomDato = this.datoStart,
            periodeTomDato = this.datoSlutt ?: "",
            sats = this.sats,
        )
}

fun PeriodeFraSkatt.sameAs(other: PeriodeTilOS): Boolean =
    this.startdato == other.fom &&
        this.sluttdato == other.tom &&
        when (other.trekkAlternativ) {
            TrekkAlternativ.LOPP -> {
                this.trekkprosent == other.sats
            }
            TrekkAlternativ.LOPM -> {
                this.trekkprosent == other.sats
            }
        }