package no.nav.sokos.utleggstrekk.database.model

import java.time.LocalDateTime

data class TrekkpaleggTable(
    val trekkpaleggTableId: Long,
    val trekkidNav: String? = null,
    val sekvensnummer: Int,
    val saksnummer: String,
    val trekkidSke: String,
    val trekkversjon: Int,
    val opprettetSke: String,
    val trekkpliktig: String,
    val skyldner: String,
    val trekkstatus: String,
    val kid: String,
    val kontonummer: String,
    val betalingsmottaker: String,
    val corrid: String,
    val status: String,
    val tidspunktSendtOs: LocalDateTime? = null,
    val tidspunktSisteStatus: LocalDateTime,
    val tidspunktOpprettet: LocalDateTime,
)


