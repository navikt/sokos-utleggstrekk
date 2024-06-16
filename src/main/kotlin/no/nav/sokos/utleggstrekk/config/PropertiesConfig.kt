package nav.no.sokos.utleggstrekk.config

import com.natpryce.konfig.ConfigurationMap
import com.natpryce.konfig.ConfigurationProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.Key
import com.natpryce.konfig.overriding
import com.natpryce.konfig.stringType
import java.io.File

object PropertiesConfig {

	private val defaultProperties = ConfigurationMap(
		mapOf(
			"NAIS_APP_NAME" to "sokos-utleggstrekk",
			"NAIS_NAMESPACE" to "okonomi",
		)
	)

	private val localDevProperties = ConfigurationMap(
		"APPLICATION_PROFILE" to Profile.LOCAL.toString(),

	  	"DATABASE_USERNAME" to "",
	    "DATABASE_PASSWORD" to "",
		"DATABASE_HOST" to "",
		"DATABASE_PORT" to "",
		"DATABASE_NAME" to "",

		"VAULT_MOUNTPATH" to "",
	)

	private val devProperties = ConfigurationMap(mapOf("APPLICATION_PROFILE" to Profile.DEV.toString()))

	private val config = when (System.getenv("NAIS_CLUSTER_NAME") ?: System.getProperty("NAIS_CLUSTER_NAME")) {
		"dev-fss" -> ConfigurationProperties.systemProperties() overriding EnvironmentVariables() overriding devProperties overriding defaultProperties
		else ->
			ConfigurationProperties.systemProperties() overriding EnvironmentVariables() overriding ConfigurationProperties.fromOptionalFile(
				File("defaults.properties")
			) overriding localDevProperties overriding defaultProperties
	}

	enum class Profile {
		LOCAL, DEV
	}

	operator fun get(key: String): String = config[Key(key, stringType)]

	data class DatabaseConfig(
		val host: String = get("DATABASE_HOST"),
		val port: String = get("DATABASE_PORT"),
		val name: String = get("DATABASE_NAME"),
		val username: String = get("DATABASE_USERNAME"),
		val password: String = get("DATABASE_PASSWORD"),
		val schema: String = get("DATABASE_SCHEMA"),
	) {
		val jdbcUrl: String = "jdbc:oracle:thin:@$host:$port/$name"
	}
}
