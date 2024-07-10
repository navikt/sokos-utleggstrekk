package no.nav.sokos.utleggstrekk.database.model

data class MidlertidigStansTable(
    val midlertidigstansid: Long,
    val utleggstrekkid_nav: Long,
    val startPeriode: String? = null,
    val sluttPeriode: String? = null,
)