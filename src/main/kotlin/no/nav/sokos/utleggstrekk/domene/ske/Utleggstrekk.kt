package no.nav.sokos.utleggstrekk.domene.ske

import kotlinx.serialization.Serializable

@Serializable
data class Utleggstrekk(
    val trekkid: String,
    val trekkversjon: Int,
    val sekvensnummer: Int,
    val opprettet: String,
    val trekkpliktig: String,
    val skyldner: String,
    val trekkstatus: String,
    val startPeriode: String? = null,
    val sluttPeriode: String? = null,
    val midlertidigStans: List<MidlertidigStans>? = null,
    val trekkbeloep: TrekkBeloep? = null,
    val trekkprosent: TrekkProsent? = null,
    val kid: String? = null,
    val kontonummer: String? = null,
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
data class MidlertidigStans(
    val startPeriode: String? = null,
    val sluttPeriode: String? = null,
)