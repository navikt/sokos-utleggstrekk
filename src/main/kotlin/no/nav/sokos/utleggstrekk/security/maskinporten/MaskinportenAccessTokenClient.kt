package no.nav.sokos.utleggstrekk.security.maskinporten

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.HttpClient
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import mu.KotlinLogging
import no.nav.sokos.utleggstrekk.config.PropertiesConfig
import java.util.Date

class MaskinportenAccessTokenClient(
    private val maskinportenConfig: PropertiesConfig.MaskinportenClientConfig,
    private val client: HttpClient,
) {
    private val logger = KotlinLogging.logger {}
    private val secureLogger = KotlinLogging.logger { }
    private val mutex = Mutex()

    @Volatile
    private lateinit var token: AccessToken

    suspend fun hentAccessToken(): String {
        val omToMinutter = Clock.System.now().plus(2, DateTimeUnit.MINUTE)

        return mutex.withLock {
            when {
                !this::token.isInitialized || token.expiresAt < omToMinutter -> {
                    token = AccessToken(hentAccessTokenFraProvider())
                    token.accessToken
                }

                else -> {
                    logger.info { "bruker samme" }
                    token.accessToken
                }
            }
        }
    }

    private suspend fun hentAccessTokenFraProvider(): Token {
//        println("Issuer")
//        println(maskinportenConfig.openIdConfiguration.issuer)
//        println("tokenendpoint")
//        println(maskinportenConfig.openIdConfiguration.tokenEndpoint)
//        println("jwksuri")
//        println(maskinportenConfig.openIdConfiguration.jwksUri)

//        println("fun hentAccessTokenFraProvider")
        val jwt =
            JWT
                .create()
                .withAudience(maskinportenConfig.openIdConfiguration.issuer)
                .withIssuer(maskinportenConfig.clientId)
                .withClaim("scope", maskinportenConfig.scopes)
                .withExpiresAt(
                    Date(
                        Clock.System
                            .now()
                            .plus(2, DateTimeUnit.MINUTE)
                            .toEpochMilliseconds(),
                    ),
                ).withIssuedAt(Date())
                .withKeyId(maskinportenConfig.rsaKey?.keyID)
                .sign(Algorithm.RSA256(null, maskinportenConfig.rsaKey?.toRSAPrivateKey()))
        val response =
            client.post(maskinportenConfig.openIdConfiguration.tokenEndpoint) {
                accept(ContentType.Application.Json)
                contentType(ContentType.Application.FormUrlEncoded)
                method = HttpMethod.Post
                setBody("grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=$jwt")
            }

        return try {
//               println(response.bodyAsText())
            response.body()
        } catch (ex: NoTransformationFoundException) {
            logger.error("Kunne ikke lese accessToken, se sikker log for meldingen som string")
            val feilmelding = response.bodyAsText()
            secureLogger.error("Feil fra tokenprovider, Token: $jwt, Feilmelding: $feilmelding")
            throw ex
        }
    }
}