package no.nav.sokos.utleggstrekk.config

import kotlinx.serialization.Serializable

import com.nimbusds.jose.jwk.RSAKey

@Serializable
data class MaskinportenClientConfig(
    val clientId: String,
    val wellKnownUrl: String,
    val scopes: String,
    val rsaKeyString: String,
) {
    val rsaKey: RSAKey? by lazy {
        RSAKey.parse(rsaKeyString)
    }
}