@file:OptIn(ExperimentalTime::class)

package no.nav.sokos.utleggstrekk.security.maskinporten

import java.time.Instant
import java.util.Date
import java.util.UUID

import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import mu.KotlinLogging

import no.nav.sokos.utleggstrekk.config.MaskinportenClientConfig
import no.nav.sokos.utleggstrekk.config.TEAM_LOGS_MARKER

class MaskinportenAccessTokenClient(
    private val maskinportenConfig: MaskinportenClientConfig,
    private val client: HttpClient,
) {
    private val logger = KotlinLogging.logger {}
    private val mutex = Mutex()

    private val timeLimit = 1.minutes

    @Volatile
    private var cachedToken: AccessToken? = null

    suspend fun getAccessToken(): String =
        mutex.withLock {
            val inOneMinute = Instant.now().plusSeconds(timeLimit.inWholeSeconds)
            val current = cachedToken

            if (current == null || current.expiresAt.isBefore(inOneMinute)) {
                cachedToken = getMaskinportenToken()
            }
            cachedToken?.token ?: throw MaskinportenException("Failed to obtain access token")
        }

    private suspend fun getMaskinportenToken(): AccessToken {
        val openIdConfiguration = getOpenIdConfiguration()
        val jwtAssertion = createJwtAssertion(openIdConfiguration.issuer, getSystembrukerClaim(maskinportenConfig.systemBrukerClaim))

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
            val msg = "Feil fra tokenprovider, Feilmelding: $feilmelding"
            logger.error { "Feil fra tokenprovider" }
            logger.error(TEAM_LOGS_MARKER, msg)
            throw MaskinportenException(msg)
        }
    }

    private suspend fun getOpenIdConfiguration(): OpenIdConfiguration =
        runCatching {
            client.get(maskinportenConfig.wellKnownUrl).body<OpenIdConfiguration>()
        }.getOrElse { exception ->
            val msg = "Feil i henting av OpenID konfigurasjon fra ${maskinportenConfig.wellKnownUrl}"
            logger.error { "$msg : ${exception.message}" }
            logger.error(TEAM_LOGS_MARKER, msg, exception)
            throw exception
        }

    private fun createJwtAssertion(audience: String, additionalClaims: Map<String, Any> = emptyMap()): String =
        SignedJWT(
            JWSHeader
                .Builder(JWSAlgorithm.RS256)
                .keyID(maskinportenConfig.rsaKey?.keyID)
                .type(JOSEObjectType.JWT)
                .build(),
            JWTClaimsSet
                .Builder()
                .issuer(maskinportenConfig.clientId)
                .audience(audience)
                .issueTime(Date.from(Instant.now()))
                .expirationTime(Date.from(Instant.now().plusSeconds(timeLimit.inWholeSeconds)))
                .jwtID(UUID.randomUUID().toString())
                .claim("scope", maskinportenConfig.scopes)
                .apply { additionalClaims.forEach { (key, value) -> claim(key, value) } }
                .build(),
        ).apply {
            sign(RSASSASigner(maskinportenConfig.rsaKey?.toRSAPrivateKey()))
        }.serialize()

    private data class AccessToken(
        val token: String,
        val expiresAt: Instant,
    ) {
        constructor(maskinportenTokenResponse: MaskinportenTokenResponse) :
            this(maskinportenTokenResponse.accessToken, Instant.now().plusSeconds(maskinportenTokenResponse.expiresIn))
    }

    private fun getSystembrukerClaim(orgNr: String) =
        mapOf(
            "authorization_details" to
                listOf(
                    mapOf(
                        "type" to "urn:altinn:systemuser",
                        "systemuser_org" to
                            mapOf(
                                "authority" to "iso6523-actorid-upis",
                                "ID" to "0192:$orgNr",
                            ),
                    ),
                ),
        )

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