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
    val opprettetSke: LocalDateTime,
    val trekkpliktig: String,
    val skyldner: String,
    val trekkstatus: String,
    val kid: String,
    val kontonummer: String,
    val betalingsmottaker: String,
    val corrid: String,
    val status: String,
    val kvitteringLOPM: String? = null,
    val kvitteringLOPP: String? = null,
    val tidspunktSendtOs: LocalDateTime? = null,
    val tidspunktSisteStatus: LocalDateTime,
    val tidspunktOpprettet: LocalDateTime,
)

enum class UtleggstrekkStatus(
    val status: String,
) {
    MOTTATT("MOTTATT"),
    SENDT("SENDT"),

    KVITTERING_OK("KVITTERING_OK"),
    KVITTERING_FEILET("KVITTERING_FEILET"),
}
