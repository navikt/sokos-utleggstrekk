package sokos.utleggstrekk.models

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

@Serializable
data class Utleggstrekk (
    val trekkid: String,
    val trekkversjon: Int,
    val opprettet: LocalDateTime,
    val trekkpliktig: String,
    val skyldner: String,
    val trekkstatus: TrekkStatus,
    val startPeriode: String? = null,
    val sluttPeriode: String? = null,
    val midlertidigStans: List<MidlertidigStans>? = null,
    val trekkbeloep: Double? = null,
    val trekkprosent: Double? = null,
    val kidnummer: String,
    val kontonummer: String
)