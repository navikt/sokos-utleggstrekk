package no.nav.sokos.utleggstrekk.database.model

import java.time.LocalDate

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime

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
    // TODO: kotlinx har med parsing å gjøre. Bytt til java LocalDateTime
    val tidspunktOpprettet: LocalDateTime =
        java.time.LocalDateTime
            .now()
            .toKotlinLocalDateTime(),
    // TODO: Hører til modellen av hva vi sender til Oppdrag.
    val kilde: String = "SKATTEETATEN",
    // TODO fjern defaultverdi
    val status: PeriodeStatus = PeriodeStatus.IKKE_SENDT,
    // TODO fjern defaultverdi
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
        tidspunktOpprettet = row.localDateTime("tidspunkt_opprettet").toKotlinLocalDateTime(),
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

    fun isExpired(): Boolean {
        val end = datoSlutt?.let { LocalDate.parse(datoSlutt) } ?: return false
        return end.isBefore(LocalDate.now()) // TODO:  Må bruke "opprettet" fra trekkversjonen fra skatt?
    }
}

fun Periode.sameAs(other: TrekkPeriodeTable): Boolean =
    this.startdato == other.datoStart &&
        this.sluttdato == other.datoSlutt &&
        when (other.trekkAlternativ) {
            TrekkAlternativ.LOPP -> {
                this.trekkprosent == other.sats
            }
            TrekkAlternativ.LOPM -> {
                this.trekkprosent == other.sats
            }
        }