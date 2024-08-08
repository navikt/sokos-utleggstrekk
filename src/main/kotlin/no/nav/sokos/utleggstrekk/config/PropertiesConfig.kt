package no.nav.sokos.utleggstrekk.config

import com.natpryce.konfig.ConfigurationMap
import com.natpryce.konfig.ConfigurationProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.Key
import com.natpryce.konfig.overriding
import com.natpryce.konfig.stringType
import com.nimbusds.jose.jwk.RSAKey
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import no.nav.sokos.utleggstrekk.client.httpClient
import java.io.File

object PropertiesConfig {
    private val defaultProperties =
        ConfigurationMap(
            mapOf(
                "NAIS_APP_NAME" to "sokos-ske-krav",
                "NAIS_NAMESPACE" to "okonomi",
            ),
        )
    private val localDevProperties =
        ConfigurationMap(
            "APPLICATION_PROFILE" to Profile.LOCAL.toString(),
            "POSTGRES_HOST" to "dev-pg.intern.nav.no",
            "POSTGRES_PORT" to "5432",
            "POSTGRES_NAME" to "sokos-utleggstrekk",
            "SKE_REST_URL" to "https://api-test.sits.no/api/utleggstrekk/v1/",
            "USE_AUTHENTICATION" to "false",
            "MQ_HOST" to "10.53.17.118",
            "MQ_PORT" to "1413",
            "MQ_QMGR_NAME" to "MQLS02",
            "MQ_CHANNEL" to "Q1_UTLEGGSTREKK",
            "MQ_SEND_TIL_OS" to "QA.DY_231.OB04_INNRAPPORTERING_TREKK",
        )

    private val devProperties = ConfigurationMap(mapOf("APPLICATION_PROFILE" to Profile.DEV.toString()))
    private val prodProperties = ConfigurationMap(mapOf("APPLICATION_PROFILE" to Profile.PROD.toString()))

    private val config =
        when (System.getenv("NAIS_CLUSTER_NAME") ?: System.getProperty("NAIS_CLUSTER_NAME")) {
            "dev-fss" -> ConfigurationProperties.systemProperties() overriding EnvironmentVariables() overriding devProperties overriding defaultProperties
            "prod-fss" -> ConfigurationProperties.systemProperties() overriding EnvironmentVariables() overriding prodProperties overriding defaultProperties
            else ->
                ConfigurationProperties.systemProperties() overriding EnvironmentVariables() overriding
                    ConfigurationProperties.fromOptionalFile(
                        File("defaults.properties"),
                    ) overriding localDevProperties overriding defaultProperties
        }

    enum class Profile {
        LOCAL,
        DEV,
        PROD,
    }

    fun isLocal() = Configuration().profile == Profile.LOCAL

    operator fun get(key: String): String = config[Key(key, stringType)]

    fun getOrEmpty(key: String): String = config.getOrElse(Key(key, stringType), "")

    data class Configuration(
        val naisAppName: String = get("NAIS_APP_NAME"),
        val profile: Profile = Profile.valueOf(this["APPLICATION_PROFILE"]),
    )

    data class MaskinportenClientConfig(
        val clientId: String = getOrEmpty("MASKINPORTEN_CLIENT_ID"),
        val authorityEndpoint: String = getOrEmpty("MASKINPORTEN_WELL_KNOWN_URL"),
        val rsaKey: RSAKey? = RSAKey.parse(getOrEmpty("MASKINPORTEN_CLIENT_JWK")),
        val scopes: String = getOrEmpty("MASKINPORTEN_SCOPES"),
    ) : JwtConfig(authorityEndpoint)

    @Serializable
    data class OpenIdConfiguration(
        @SerialName("jwks_uri")
        val jwksUri: String,
        @SerialName("issuer")
        val issuer: String,
        @SerialName("token_endpoint")
        val tokenEndpoint: String,
    )

    data class SKEConfig(
        val skeRestUrl: String = getOrEmpty("SKE_REST_URL"),
    )

    data class PostgresConfig(
        val host: String = getOrEmpty("POSTGRES_HOST"),
        val port: String = getOrEmpty("POSTGRES_PORT"),
        val name: String = getOrEmpty("POSTGRES_NAME"),
        val username: String = getOrEmpty("POSTGRES_USERNAME").trim(),
        val password: String = getOrEmpty("POSTGRES_PASSWORD").trim(),
        val vaultMountPath: String = getOrEmpty("VAULT_MOUNTPATH"),
    ) {
        val jdbcUrl: String = "jdbc:postgresql://$host:$port/$name"
        val adminUser = "$name-admin"
        val user = "$name-user"
    }

    open class JwtConfig(
        private val wellKnownUrl: String,
    ) {
        val openIdConfiguration: OpenIdConfiguration by lazy {
            runBlocking {
                httpClient.get(wellKnownUrl).body()
            }
        }
    }

    data class MqProperties(
        val queue: String = getOrEmpty("MQ_SEND_TIL_OS").also(::println),
        val host: String = getOrEmpty("MQ_HOST").also(::println),
        val port: String = getOrEmpty("MQ_PORT").also(::println),
        val qmgrName: String = getOrEmpty("MQ_QMGR_NAME").also(::println),
        val channel: String = getOrEmpty("MQ_CHANNEL").also(::println),
        val username: String = getOrEmpty("MQ_USERNAME").also(::println),
        val password: String = getOrEmpty("MQ_PASSWORD").also(::println),
        val inqUsername: String = getOrEmpty("MQ_INQ_USERNAME").also(::println),
        val inqPassword: String = getOrEmpty("MQ_INQ_PASSWORD").also(::println),
    )
}