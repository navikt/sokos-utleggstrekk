package no.nav.sokos.utleggstrekk.database.model

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime

import kotliquery.Row

import no.nav.sokos.utleggstrekk.domene.nav.Periode
import no.nav.sokos.utleggstrekk.domene.nav.TrekkAlternativ

data class TrekkPeriodeTable(
    val trekkPeriodeTableId: Int,
    val sekvensnummer: Int,
    val trekkidSke: String,
    val trekkversjon: Int,
    val datoStart: String,
    val datoSlutt: String,
    val sats: Double,
    val trekkAlternativ: TrekkAlternativ,
    val tidspunktOpprettet: LocalDateTime =
        java.time.LocalDateTime
            .now()
            .toKotlinLocalDateTime(), // TODO: kotlinx har med parsing å gjøre. Bytt til java LocalDateTime
    val kilde: String = "SKATTEETATEN", // TODO: Hører til modellen av hva vi sender til Oppdrag.
) {
    constructor(row: Row) : this(
        trekkPeriodeTableId = row.int("id"),
        sekvensnummer = row.int("sekvensnummer"),
        trekkidSke = row.string("trekkid_ske"),
        trekkversjon = row.int("trekkversjon"),
        datoStart = row.string("dato_start"),
        datoSlutt = row.string("dato_slutt"),
        sats = row.double("sats"),
        trekkAlternativ = TrekkAlternativ.valueOf(row.string("trekkalternativ")),
        kilde = row.string("kilde"),
        tidspunktOpprettet = row.localDateTime("tidspunkt_opprettet").toKotlinLocalDateTime(),
    )

    fun toTrekkDokumentPeriode() =
        Periode(
            periodeFomDato = this.datoStart,
            periodeTomDato = this.datoSlutt,
            sats = this.sats,
        )
}