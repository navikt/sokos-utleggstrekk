package no.nav.sokos.utleggstrekk.domene.ske

data class Feil(
    val kode: String? = null,
    val melding: String? = null,
    val korrelasjonsid: String? = null
) {
}