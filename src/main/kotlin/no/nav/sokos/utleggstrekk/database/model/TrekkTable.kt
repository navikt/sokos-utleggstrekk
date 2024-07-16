package no.nav.sokos.utleggstrekk.database.model

import java.time.LocalDateTime


data class TrekkTable(
    val trekktableid: Long,
    val sekvensnr: Int,
    val trekkid: String,
    val trekkversjon: Int,
    val trekkopprettet: LocalDateTime,
    val trekkpliktig: String,
    val skyldner: String,
    val trekkstatus: String,
    val startPeriode: String,
    val sluttPeriode: String,
    val trekkbelop: Double? = null,
    val trekkprosent: Double? = null,
    val kid: String,
    val kontonummer: String,
    val corrid: String,
    val status: String,
    val tidspunktMottatt: LocalDateTime,
    val tidspunktSendtOs: LocalDateTime? = null,
    val tidspunktSisteStatus: LocalDateTime,
    val tidspunktOpprettet: LocalDateTime,
)