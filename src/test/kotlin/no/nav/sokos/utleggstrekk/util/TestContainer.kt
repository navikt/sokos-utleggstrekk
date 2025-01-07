package no.nav.sokos.utleggstrekk.util

import io.kotest.extensions.testcontainers.toDataSource
import no.nav.sokos.utleggstrekk.config.PropertiesConfig
import no.nav.sokos.utleggstrekk.database.PostgresDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.ext.ScriptUtils
import org.testcontainers.jdbc.JdbcDatabaseDelegate
import org.testcontainers.utility.DockerImageName

class TestContainer {
    private val properties = PropertiesConfig.PostgresConfig()
    private val dockerImageName = "postgres:latest"

    private val container =
        PostgreSQLContainer<Nothing>(DockerImageName.parse(dockerImageName))
            .apply {
                withReuse(false)
                withUsername(properties.adminUser)
                start()
            }

    private val ds =
        container.toDataSource {
            maximumPoolSize = 100
            minimumIdle = 1
            isAutoCommit = false
        }
    val dataSource = ds

    init {
        PostgresDataSource.migrate(ds)
    }

    fun migrate(script: String = "") {
        if (script.isNotEmpty()) loadInitScript(script)
    }

    private fun loadInitScript(name: String) = ScriptUtils.runInitScript(JdbcDatabaseDelegate(container, ""), name)
}
