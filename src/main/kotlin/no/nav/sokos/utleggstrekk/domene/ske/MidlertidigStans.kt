package no.nav.sokos.utleggstrekk.domene.ske

import kotlinx.serialization.Serializable

@Serializable
data class MidlertidigStans (
    val startPeriode: String? = null,
    val sluttPeriode: String? = null
)