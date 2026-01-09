package no.nav.sokos.utleggstrekk.config

import kotlinx.serialization.Serializable

@Serializable
data class SkeConfig(
    val skeRestUrl: String,
    val skeOrgNr: String,
    val skeKontoNr: String,
    val skeTSSId: String,
)