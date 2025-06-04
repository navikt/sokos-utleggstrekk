package no.nav.sokos.utleggstrekk.database.model

import kotlinx.datetime.LocalDateTime

data class FeilkodeTable(
    val feilkodeTableId: Int,
    val trekkIdNav: Int,
    val corrId: String,
    val trekkAlternativ: Int,
    val feilkode: String,
    val beskrivelse: String?,
    //@Serializable(with = JavaLocaldateTimeSerializer::class)
    val tidspunktOpprettet: LocalDateTime,
)
