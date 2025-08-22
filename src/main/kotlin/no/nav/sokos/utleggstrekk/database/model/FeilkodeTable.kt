package no.nav.sokos.utleggstrekk.database.model

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import kotliquery.Row

data class FeilkodeTable(
    val feilkodeTableId: Long,
    val trekkIdNav: String,
    val corrId: String,
    val trekkAlternativ: String,
    val feilkode: String,
    val beskrivelse: String?,
    val tidspunktOpprettet: LocalDateTime,
) {
    constructor(row: Row) : this(
        feilkodeTableId = row.long("id"),
        trekkIdNav = row.string("kreditor_trekk_id"),
        trekkAlternativ = row.string("trekkalternativ"),
        corrId = row.string("corr_id"),
        feilkode = row.string("feilkode"),
        beskrivelse = row.string("beskrivelse"),
        tidspunktOpprettet = row.localDateTime("tidspunkt_opprettet").toKotlinLocalDateTime(),
    )
}
