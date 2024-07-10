package no.nav.sokos.utleggstrekk.domene.ske

import kotlinx.serialization.Serializable

@Serializable
data class Utleggstrekk (
    val trekkid: String,
    val trekkversjon: Int,
    val opprettet: String,
    val trekkpliktig: String,
    val skyldner: String,
    val trekkstatus: String,
    val startPeriode: String? = null,
    val sluttPeriode: String? = null,
    val midlertidigStans: List<MidlertidigStans>? = null,
    val trekkbeloep: TrekkBeloep? = null,
    val trekkprosent: TrekkProsent? = null,
    val kidnummer: String? = null,
    val kontonummer: String? = null
)

@Serializable
data class TrekkProsent(
    val trekkprosent: Double? = null
)
@Serializable
data class TrekkBeloep(
    val trekkBeloep: Double? = null
)
@Serializable
data class MidlertidigStans (
    val startPeriode: String? = null,
    val sluttPeriode: String? = null
)