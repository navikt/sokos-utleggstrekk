package no.nav.sokos.utleggstrekk.util

import io.kotest.extensions.testcontainers.toDataSource
import org.flywaydb.core.Flyway
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.ext.ScriptUtils
import org.testcontainers.jdbc.JdbcDatabaseDelegate
import org.testcontainers.utility.DockerImageName

import no.nav.sokos.utleggstrekk.AppSettings.postgresConfig

class TestContainer {
    private val dockerImageName = "postgres:latest"
    private val container =
        PostgreSQLContainer<Nothing>(DockerImageName.parse(dockerImageName)).apply {
            withReuse(false)
            withUsername(postgresConfig.user)
            start()
        }

    val dataSource =
        container.toDataSource {
            maximumPoolSize = 100
            minimumIdle = 1
            isAutoCommit = false
        }

    init {
        Flyway
            .configure()
            .dataSource(dataSource)
            .initSql("""SET ROLE "${postgresConfig.user}"""")
            .lockRetryCount(-1)
            .validateMigrationNaming(true)
            .load()
            .migrate()
            .migrationsExecuted
    }

    fun loadInitScript(name: String) = ScriptUtils.runInitScript(JdbcDatabaseDelegate(container, ""), name)
}