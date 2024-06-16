package nav.no.sokos.utleggstrekk.config

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.annotation.JsonProperty
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import nav.no.sokos.utleggstrekk.httpClient
import java.net.URI
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger { }

data class AzureConfiguration(
    val appName:String = readProperty("NAIS_APP_NAME"),
    val azureAd: AzureAd = AzureAd(),
    val useAuthentication: Boolean = readProperty("USE_AUTHENTICATION", "true") != "false"
)
{
    data class AzureAd(
        val clientId: String = readProperty("AZURE_APP_CLIENT_ID", ""),
        val authorityEndpoint: String = readProperty("AZURE_APP_WELL_KNOWN_URL", ""),
    ) {
        val openIdConfiguration: AzureAdOpenIdConfiguration by lazy {
            runBlocking { httpClient.get(authorityEndpoint).body() }
        }
        val jwkProvider: JwkProvider by lazy {
            JwkProviderBuilder(URI(openIdConfiguration.jwksUri).toURL())
                .cached(10, 24, TimeUnit.HOURS)       // cache up to 10 JWKs for 24 hours
                .rateLimited(
                    10,
                    1,
                    TimeUnit.MINUTES
                ) // if not cached, only allow max 10 different keys per minute to be
                .build()                              // fetched from external provider
        }
    }

    data class AzureAdOpenIdConfiguration(
        @JsonProperty("jwks_uri")
        val jwksUri: String,
        @JsonProperty("issuer")
        val issuer: String,
        @JsonProperty("token_endpoint")
        val tokenEndpoint: String,
        @JsonProperty("authorization_endpoint")
        val authorizationEndpoint: String
    )

}
private fun readProperty(name: String, default: String? = null) =
    System.getenv(name)
        ?: System.getProperty(name)
        ?: default.takeIf { it != null }?.also { logger.warn( "Using default value for property $name" ) }
        ?: throw RuntimeException("Mandatory property '$name' was not found")
