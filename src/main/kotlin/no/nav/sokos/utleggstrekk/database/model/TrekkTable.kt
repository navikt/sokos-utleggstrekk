package no.nav.sokos.utleggstrekk.database.model

import kotlinx.datetime.LocalDateTime

data class TrekkTable(
    val trekktableid: Long,
    val sekvensnr: Int,
    val trekkid: String,
    val trekkversjon: Int,
    val trekkopprettet: LocalDateTime,
    val trekkpliktig: String,
    val skyldner: String,
    val trekkstatus: String,
    val startPeriode: String? = null,
    val sluttPeriode: String? = null,
    val trekkbelop: Double? = null,
    val trekkprosent: Double? = null,
    val kid: String? = null,
    val kontonummer: String? = null,
    val corrid: String,
    val status: String,
    val tidspunktMottatt: LocalDateTime,
    val tidspunktSendtOs: LocalDateTime? = null,
    val tidspunktSisteStatus: LocalDateTime,
    val tidspunktOpprettet: LocalDateTime,
)