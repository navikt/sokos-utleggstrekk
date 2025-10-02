package no.nav.sokos.utleggstrekk.config

import java.net.URI
import java.util.concurrent.TimeUnit

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import io.ktor.client.call.body
import io.ktor.client.request.get

import no.nav.sokos.utleggstrekk.client.httpClient
import no.nav.sokos.utleggstrekk.config.PropertiesConfig.get

data class AzureConfiguration(
    val appName: String = get("NAIS_APP_NAME"),
    val azureAd: AzureAd = AzureAd(),
    val useAuthentication: Boolean = get("USE_AUTHENTICATION") != "false",
) {
    data class AzureAd(
        val clientId: String = get("AZURE_APP_CLIENT_ID"),
        val authorityEndpoint: String = get("AZURE_APP_WELL_KNOWN_URL"),
    ) {
        val openIdConfiguration: AzureAdOpenIdConfiguration by lazy {
            runBlocking { httpClient.get(authorityEndpoint).body() }
        }
        val jwkProvider: JwkProvider by lazy {
            JwkProviderBuilder(URI(openIdConfiguration.jwksUri).toURL())
                .cached(10, 24, TimeUnit.HOURS) // cache up to 10 JWKs for 24 hours
                .rateLimited(
                    10,
                    1,
                    TimeUnit.MINUTES,
                ) // if not cached, only allow max 10 different keys per minute to be
                .build() // fetched from external provider
        }
    }

    @Serializable
    data class AzureAdOpenIdConfiguration(
        @SerialName("jwks_uri")
        val jwksUri: String,
        @SerialName("issuer")
        val issuer: String,
        @SerialName("token_endpoint")
        val tokenEndpoint: String,
        @SerialName("authorization_endpoint")
        val authorizationEndpoint: String,
    )
}