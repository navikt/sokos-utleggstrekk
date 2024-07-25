package no.nav.sokos.utleggstrekk.database.model

data class MidlertidigStansTable(
    val midlertidigstansid: Long,
    val trekksekvensnr: Long,
    val startPeriode: String,
    val sluttPeriode: String,
)