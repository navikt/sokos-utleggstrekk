package no.nav.sokos.utleggstrekk.database.model

import kotlinx.serialization.Serializable
import no.nav.sokos.utleggstrekk.utils.JavaLocaldateTimeSerializer
import java.time.LocalDateTime

@Serializable
data class UtleggstrekkTable(
    val utleggstrekkTableId: Long,
    val trekkidNav: String? = null,
    val sekvensnummer: Int,
    val saksnummer: String,
    val trekkidSke: String,
    val trekkidSkeOS: String? = null,
    val trekkversjon: Int,
    @Serializable(with = JavaLocaldateTimeSerializer::class)
    val opprettetSke: LocalDateTime,
    val trekkpliktig: String,
    val skyldner: String,
    val trekkstatus: String,
    val trekkAlternativ: String? = null,
    val kid: String,
    val kontonummer: String,
    val betalingsmottaker: String,
    val corrid: String,
    val status: String,
    @Serializable(with = JavaLocaldateTimeSerializer::class)
    val tidspunktSendtOs: LocalDateTime? = null,
    @Serializable(with = JavaLocaldateTimeSerializer::class)
    val tidspunktSisteStatus: LocalDateTime,
    @Serializable(with = JavaLocaldateTimeSerializer::class)
    val tidspunktOpprettet: LocalDateTime,
)


