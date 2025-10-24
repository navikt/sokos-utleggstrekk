package no.nav.sokos.utleggstrekk.database.model

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime

import kotliquery.Row

import no.nav.sokos.utleggstrekk.domene.nav.TrekkAlternativ

data class Feilmelding(
    val id: Long,
    val kreditorTrekkId: String,
    val transaksjonsId: String,
    val trekkAlternativ: TrekkAlternativ,
    val feilkode: String,
    val beskrivelse: String?,
    val tidspunktOpprettet: LocalDateTime,
) {
    constructor(row: Row) : this(
        id = row.long("id"),
        kreditorTrekkId = row.string("kreditor_trekk_id"),
        trekkAlternativ = TrekkAlternativ.valueOf(row.string("trekkalternativ").uppercase()),
        transaksjonsId = row.string("transaksjons_id"),
        feilkode = row.string("feilkode"),
        beskrivelse = row.string("beskrivelse"),
        tidspunktOpprettet = row.localDateTime("tidspunkt_opprettet").toKotlinLocalDateTime(),
    )
}