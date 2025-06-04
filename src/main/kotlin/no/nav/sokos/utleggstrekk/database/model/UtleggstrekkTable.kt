package no.nav.sokos.utleggstrekk.database.model

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class UtleggstrekkTable(
    val utleggstrekkTableId: Long,
    val trekkidNav: String? = null,
    val sekvensnummer: Int,
    val saksnummer: String,
    val trekkidSke: String,
    val trekkversjon: Int,
    //@Serializable(with = JavaLocaldateTimeSerializer::class)
    val opprettetSke: LocalDateTime,
    val trekkpliktig: String,
    val skyldner: String,
    val trekkstatus: String,
    val kid: String,
    val kontonummer: String,
    val betalingsmottaker: String,
    val corrid: String,
    val status: String,
    //@Serializable(with = JavaLocaldateTimeSerializer::class)
    val tidspunktSendtOs: LocalDateTime? = null,
    //@Serializable(with = JavaLocaldateTimeSerializer::class)
    val tidspunktSisteStatus: LocalDateTime,
    //@Serializable(with = JavaLocaldateTimeSerializer::class)
    val tidspunktOpprettet: LocalDateTime,
)


