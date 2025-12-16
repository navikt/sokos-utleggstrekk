package no.nav.sokos.utleggstrekk.domene.ske

import kotlinx.serialization.Serializable

@Serializable
data class SkeErrorMessage(
    val kode: String,
    val melding: String,
    val korrelasjonsid: String,
) {
    fun description() = "$kode $melding. KorrelasjonsId: $korrelasjonsid"
}