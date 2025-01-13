package no.nav.sokos.utleggstrekk.domene.ske

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class Trekkpaalegg(
    val trekkid: String,
    val trekkversjon: Int,
    val sekvensnummer: Int,
    val opprettet: LocalDateTime,
    val saksnummer: String,
    val trekkpliktig: String,
    val skyldner: String,
    val trekkstatus: Trekkstatus,
    val trekkstoerrelseForPeriode: List<TrekkstorrelseForPeriode>,
    val betalingsinformasjon: Betalingsinformasjon,
)

@Serializable
data class TrekkProsent(
    val trekkprosent: Double? = null,
)

@Serializable
data class TrekkBeloep(
    val trekkbeloep: Double? = null,
)

@Serializable
enum class Trekkstatus(val value: String){
    AKTIV("aktiv"),
    AVSLUTTET("avsluttet")
}

@Serializable
data class TrekkstorrelseForPeriode(
    val startdato: LocalDate,
    val sluttdato: LocalDate?,
    val trekkbelop: TrekkBeloep?,
    val trekkprosent: TrekkProsent?
)

@Serializable
data class Betalingsinformasjon(
    val betalingsmottaker: String,
    val kidnummer: String,
    val kontonummer: String
)
