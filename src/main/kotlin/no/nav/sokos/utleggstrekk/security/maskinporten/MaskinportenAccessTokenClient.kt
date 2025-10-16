@file:OptIn(ExperimentalTime::class)

package no.nav.sokos.utleggstrekk.security.maskinporten

import java.time.Instant
import java.util.Date
import java.util.UUID

import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import mu.KotlinLogging

import no.nav.sokos.utleggstrekk.config.PropertiesConfig

class MaskinportenAccessTokenClient(
    private val maskinportenConfig: PropertiesConfig.MaskinportenClientConfig,
    private val client: HttpClient,
) {
    private val logger = KotlinLogging.logger {}
    private val mutex = Mutex()

    private val timeLimit = 60.seconds

    @Volatile private var cachedToken: AccessToken? = null

    suspend fun getAccessToken(): String =
        mutex.withLock {
            val inTwoMinutes = Instant.now().plusSeconds(timeLimit.inWholeSeconds)
            val current = cachedToken

            if (current == null || current.expiresAt.isBefore(inTwoMinutes)) {
                cachedToken = getMaskinportenToken()
            }

            cachedToken!!.token
        }

    private suspend fun getMaskinportenToken(): AccessToken {
        val openIdConfiguration = getOpenIdConfiguration()
        val jwtAssertion = createJwtAssertion(openIdConfiguration.issuer)
        val response =
            client
                .submitForm(
                    url = openIdConfiguration.tokenEndpoint,
                    formParameters =
                        Parameters.build {
                            append("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
                            append("assertion", jwtAssertion)
                        },
                )
        return if (response.status.isSuccess()) {
            AccessToken(response.body<MaskinportenTokenResponse>())
        } else {
            val feilmelding = response.body<TokenError>()
            logger.error("Feil fra tokenprovider, Feilmelding: $feilmelding")
            throw MaskinportenException("Feil fra tokenprovider, Feilmelding: $feilmelding")
        }
    }

    private suspend fun getOpenIdConfiguration(): OpenIdConfiguration =
        runCatching {
            client.get(maskinportenConfig.wellKnownUrl).body<OpenIdConfiguration>()
        }.getOrElse { exception ->
            logger.error(exception) { "Feil i henting av OpenID konfigurasjon fra ${maskinportenConfig.wellKnownUrl}: ${exception.message}" }
            throw exception
        }

    private fun createJwtAssertion(issuer: String): String =
        JWT
            .create()
            .withIssuer(maskinportenConfig.clientId)
            .withAudience(issuer)
            .withClaim("scope", maskinportenConfig.scopes)
            .withExpiresAt(Date.from(Instant.now().plusSeconds(timeLimit.inWholeSeconds)))
            .withIssuedAt(Date())
            .withKeyId(maskinportenConfig.rsaKey?.keyID)
            .withJWTId(UUID.randomUUID().toString())
            .sign(Algorithm.RSA256(null, maskinportenConfig.rsaKey?.toRSAPrivateKey()))

    private data class AccessToken(
        val token: String,
        val expiresAt: Instant,
    ) {
        constructor(maskinportenTokenResponse: MaskinportenTokenResponse) :
            this(maskinportenTokenResponse.accessToken, Instant.now().plusSeconds(maskinportenTokenResponse.expiresIn))
    }

    @Serializable
    private data class MaskinportenTokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("expires_in") val expiresIn: Long,
        @SerialName("token_type") val tokenType: String,
    )

    @Serializable
    private data class TokenError(
        @SerialName("error") val error: String,
        @SerialName("error_description") val errorDescription: String,
        @SerialName("error_uri") val errorUri: String? = null,
    )

    @Serializable
    private data class OpenIdConfiguration(
        @SerialName("jwks_uri") val jwksUri: String,
        @SerialName("issuer") val issuer: String,
        @SerialName("token_endpoint") val tokenEndpoint: String,
    )

    class MaskinportenException(message: String) : Exception(message)
}
