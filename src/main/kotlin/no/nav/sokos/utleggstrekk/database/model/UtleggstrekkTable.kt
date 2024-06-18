package no.nav.sokos.utleggstrekk.database.model

import kotlinx.datetime.LocalDateTime

data class UtleggstrekkTable (
    val trekkid_nav: Long,
    val trekkid_ske: String,
    val trekkversjon_ske: Int,
    val trekkopprettet: LocalDateTime,
    val trekkpliktig: String,
    val skyldner: String,
    val trekkstatus: String,
    val startPeriode: String? = null,
    val sluttPeriode: String? = null,
    val trekkbeloep: Double? = null,
    val trekkprosent: Double? = null,
    val kidnummer: String? = null,
    val kontonummer: String? = null,
    val corr_id: String,
    val tidspunkt_mottatt: LocalDateTime,
    val tidspunkt_sendt_os: LocalDateTime? = null,
    val tidspunkt_siste_status: LocalDateTime,
    val tidspunkt_opprettet: LocalDateTime,
)
