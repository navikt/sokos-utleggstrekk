package no.nav.sokos.utleggstrekk.config

import java.io.File

import com.natpryce.konfig.ConfigurationMap
import com.natpryce.konfig.ConfigurationProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.Key
import com.natpryce.konfig.overriding
import com.natpryce.konfig.stringType

object PropertiesConfigOld {
    private val defaultProperties =
        ConfigurationMap(
            mapOf(
                "NAIS_APP_NAME" to "sokos-utleggstrekk",
                "NAIS_NAMESPACE" to "okonomi",
                "AZURE_APP_PROVIDER_NAME" to "azureAd",
            ),
        )
    private val localDevProperties =
        ConfigurationMap(
            "APPLICATION_PROFILE" to Profile.LOCAL.toString(),
            "POSTGRES_HOST" to "dev-pg.intern.nav.no",
            "POSTGRES_PORT" to "5432",
            "POSTGRES_NAME" to "sokos-utleggstrekk",
            "POSTGRES_USERNAME" to "sokos-utleggstrekk",
            "NAIS_POD_NAME" to "local-no-pod",
            "SKE_REST_URL" to "https://api-test.sits.no/api/trekkpaalegg/v1",
            "USE_AUTHENTICATION" to "false",
            "MQ_HOSTNAME" to "10.53.17.118",
            "MQ_PORT" to "1413",
            "MQ_QUEUE_MANAGER_NAME" to "MQLS02",
            "MQ_CHANNEL" to "Q1_UTLEGGSTREKK",
            "MQ_QUEUE_NAME" to "QA.Q1_231.OB04_TREKK_FRASKATT_JSON",
            "MQ_REPLYQUEUE_NAME" to "QA.Q1_SOKOS_UTLEGGSTREKK.KVITTERING",
            "UNLEASHED_DEFAULT_IS_ENABLED" to "true",
            "SCHEDULER_ACTIVE" to "true",
            "MASKINPORTEN_SYSTEMBRUKER_CLAIM" to "312978083",
            "SKE_ORGNR" to "971648199",
            "SKE_KONTONR" to "70213997155",
            "SKE_TSSID" to "80000423362",
        )

    private val devProperties = ConfigurationMap(mapOf("APPLICATION_PROFILE" to Profile.DEV.toString()))
    private val prodProperties = ConfigurationMap(mapOf("APPLICATION_PROFILE" to Profile.PROD.toString()))

    private val config =
        when (System.getenv("NAIS_CLUSTER_NAME") ?: System.getProperty("NAIS_CLUSTER_NAME")) {
            "dev-fss" -> {
                ConfigurationProperties.systemProperties() overriding EnvironmentVariables() overriding devProperties overriding
                    defaultProperties
            }

            "prod-fss" -> {
                ConfigurationProperties.systemProperties() overriding EnvironmentVariables() overriding prodProperties overriding
                    defaultProperties
            }

            else -> {
                ConfigurationProperties.systemProperties() overriding EnvironmentVariables() overriding
                    ConfigurationProperties.fromOptionalFile(
                        File("defaults.properties"),
                    ) overriding localDevProperties overriding defaultProperties
            }
        }

    enum class Profile {
        LOCAL,
        DEV,
        PROD,
    }

    val isLocal = Configuration().profile == Profile.LOCAL

    operator fun get(key: String): String = config[Key(key, stringType)]

    fun getOrEmpty(key: String): String = config.getOrElse(Key(key, stringType), "")

    fun getOrNull(key: String): String? = config.getOrElse(Key(key, stringType), null)

    data class Configuration(
        val naisAppName: String = get("NAIS_APP_NAME"),
        val naisPodName: String = get("NAIS_POD_NAME"),
        val profile: Profile = Profile.valueOf(get("APPLICATION_PROFILE")),
        val unleashEnabled: Boolean = get("UNLEASHED_DEFAULT_IS_ENABLED").toBoolean(),
    )

/*
    data class MaskinportenClientConfig(
        val clientId: String = getOrEmpty("MASKINPORTEN_CLIENT_ID"),
        val wellKnownUrl: String = getOrEmpty("MASKINPORTEN_WELL_KNOWN_URL"),
        val rsaKey: RSAKey? = RSAKey.parse(getOrEmpty("MASKINPORTEN_CLIENT_JWK")),
        val scopes: String = getOrEmpty("MASKINPORTEN_SCOPES"),
        val systemBrukerClaim: String = getOrEmpty("MASKINPORTEN_SYSTEMBRUKER_CLAIM"),
    )

    data object SlackConfig {
        val url: String = get("SOKOS_UTLEGGSTREKK_SLACK_WEBHOOK_URL").trim()
    }

    data class SKEConfig(
        val skeRestUrl: String = getOrEmpty("SKE_REST_URL"),
        val skeOrgNr: String = getOrEmpty("SKE_ORGNR"),
        val skeKontoNr: String = getOrEmpty("SKE_KONTONR"),
        val skeTSSId: String = getOrEmpty("SKE_TSSID"),
    )
*/

    data object PostgresConfig {
        val host: String = getOrEmpty("POSTGRES_HOST")
        val port: String = getOrEmpty("POSTGRES_PORT")
        val name: String = getOrEmpty("POSTGRES_NAME")
        val username: String = getOrEmpty("POSTGRES_USERNAME").trim()
        val password: String = getOrEmpty("POSTGRES_PASSWORD").trim()
        val jdbcUrl: String = getOrEmpty("POSTGRES_JDBC_URL")
        val user = getOrEmpty("POSTGRES_USERNAME")
    }

    data class MQProperties(
        val hostname: String = get("MQ_HOSTNAME"),
        val port: Int = get("MQ_PORT").toInt(),
        val mqQueueManagerName: String = get("MQ_QUEUE_MANAGER_NAME"),
        val mqChannelName: String = get("MQ_CHANNEL"),
        val queueName: String = get("MQ_QUEUE_NAME"),
        val replyQueueName: String = get("MQ_REPLYQUEUE_NAME"),
        val username: String = get("MQ_USERNAME"),
        val password: String = get("MQ_PASSWORD"),
        val userAuth: Boolean = true,
    )

    data object UnleashProperties {
        val unleashAPI = getOrEmpty("UNLEASH_SERVER_API_URL")
        val apiKey = getOrEmpty("UNLEASH_SERVER_API_TOKEN")
        val environment = getOrEmpty("UNLEASH_SERVER_API_ENV")
    }
}