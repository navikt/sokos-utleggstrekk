package no.nav.sokos.utleggstrekk.util

import io.kotest.extensions.testcontainers.toDataSource
import org.flywaydb.core.Flyway
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.ext.ScriptUtils
import org.testcontainers.jdbc.JdbcDatabaseDelegate
import org.testcontainers.utility.DockerImageName

import no.nav.sokos.utleggstrekk.config.PropertiesConfig

class TestContainer {
    private val properties = PropertiesConfig.PostgresProperties
    private val dockerImageName = "postgres:latest"
    private val container =
        PostgreSQLContainer<Nothing>(DockerImageName.parse(dockerImageName)).apply {
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

    init {
        Flyway
            .configure()
            .dataSource(ds)
            .initSql("""SET ROLE "${PropertiesConfig.PostgresProperties.adminUser}"""")
            .lockRetryCount(-1)
            .validateMigrationNaming(true)
            .load()
            .migrate()
            .migrationsExecuted
    }

    val dataSource = ds

    fun loadInitScript(name: String) = ScriptUtils.runInitScript(JdbcDatabaseDelegate(container, ""), name)
}
