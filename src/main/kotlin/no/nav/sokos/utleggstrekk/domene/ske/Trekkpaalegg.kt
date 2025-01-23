package no.nav.sokos.utleggstrekk.domene.ske

import kotlinx.serialization.Serializable

@Serializable
data class Trekkpaalegg(
    val trekkid: String,
    val sekvensnummer: Int,
    val trekkversjon: Int,
    val opprettet: String,
    val saksnummer: String,
    val trekkpliktig: String,
    val skyldner: String,
    val trekkstatus: String,
    val trekkstoerrelseForPeriode: List<TrekkstorrelseForPeriode>,
    val betalingsinformasjon: Betalingsinformasjon,
)

@Serializable
data class Trekkprosent(
    val trekkprosent: Double? = null,
)

@Serializable
data class Trekkbeloep(
    val trekkbeloep: Double? = null,
)

@Serializable
enum class Trekkstatus(val value: String) {
    AKTIV("aktiv"),
    AVSLUTTET("avsluttet")
}

@Serializable
data class TrekkstorrelseForPeriode(
    val startdato: String,
    val sluttdato: String?,
    val trekkbeloep: Trekkbeloep?,
    val trekkprosent: Trekkprosent?
)

@Serializable
data class Betalingsinformasjon(
    val betalingsmottaker: String,
    val kidnummer: String,
    val kontonummer: String
)


