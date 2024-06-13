package sokos.utleggstrekk.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer

@Serializable
data class MidlertidigStans (
    val startPeriode: String? = null,
    val sluttPeriode: String? = null
) {
}