package no.nav.sokos.utleggstrekk.util

import com.zaxxer.hikari.HikariDataSource
import io.kotest.extensions.testcontainers.toDataSource
import no.nav.sokos.utleggstrekk.config.PropertiesConfig
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.ext.ScriptUtils
import org.testcontainers.jdbc.JdbcDatabaseDelegate

class TestContainer {
    fun startContainer(script: String = "", initScripts: List<String> = emptyList()): HikariDataSource =
        createContainer(script, initScripts)
            .toDataSource {
                username = PropertiesConfig.PostgresConfig().adminUser
                password = "test"
                maximumPoolSize = 100
                minimumIdle = 1
                isAutoCommit = false
            }

    private fun createContainer(script: String, scripts: List<String> = emptyList()): PostgreSQLContainer<Nothing> {
        val container =
            PostgreSQLContainer<Nothing>("postgres:latest").apply {
                withExposedPorts(5432)
                withUsername(PropertiesConfig.PostgresConfig().adminUser)
                withPassword("test")
                withDatabaseName("sokos-utleggstrekk")
                start()
            }

        if (scripts.isNotEmpty()) {
            scripts.forEach {
                ScriptUtils.runInitScript(JdbcDatabaseDelegate(container, ""), it)
            }
        }
        if (script.isNotBlank()) ScriptUtils.runInitScript(JdbcDatabaseDelegate(container, ""), script)

        return container
    }
}