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
import no.nav.sokos.utleggstrekk.httpClient
import java.io.File

object PropertiesConfig {

	private val defaultProperties = ConfigurationMap(
		mapOf(
			"NAIS_APP_NAME" to "sokos-ske-krav",
			"NAIS_NAMESPACE" to "okonomi",
		)
	)
	private val localDevProperties = ConfigurationMap(
		"APPLICATION_PROFILE" to Profile.LOCAL.toString(),
		"POSTGRES_HOST" to "dev-pg.intern.nav.no",
	)

	private val devProperties = ConfigurationMap(mapOf("APPLICATION_PROFILE" to Profile.DEV.toString()))
	private val prodProperties = ConfigurationMap(mapOf("APPLICATION_PROFILE" to Profile.PROD.toString()))

	private val config = when (System.getenv("NAIS_CLUSTER_NAME") ?: System.getProperty("NAIS_CLUSTER_NAME")) {
		"dev-fss" -> ConfigurationProperties.systemProperties() overriding EnvironmentVariables() overriding devProperties overriding defaultProperties
		"prod-fss" -> ConfigurationProperties.systemProperties() overriding EnvironmentVariables() overriding prodProperties overriding defaultProperties
		else ->
			ConfigurationProperties.systemProperties() overriding EnvironmentVariables() overriding ConfigurationProperties.fromOptionalFile(
				File("defaults.properties")
			) overriding localDevProperties overriding defaultProperties
	}

	enum class Profile {
		LOCAL, DEV, PROD
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
		@SerialName("jwks_uri") val jwksUri: String,
		@SerialName("issuer") val issuer: String,
		@SerialName("token_endpoint") val tokenEndpoint: String,
	)

	data class SKEConfig(
		val skeRestUrl: String = getOrEmpty("SKE_REST_URL")
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
	}

	open class JwtConfig(private val wellKnownUrl: String) {
		val openIdConfiguration: OpenIdConfiguration by lazy {
			runBlocking {
				httpClient.get(wellKnownUrl).body()
			}
		}
	}
}
