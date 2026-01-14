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

    operator fun get(key: String): String = config[Key(key, stringType)]
}