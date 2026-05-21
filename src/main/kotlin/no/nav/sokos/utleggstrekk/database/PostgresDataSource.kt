package no.nav.sokos.utleggstrekk.database

import java.time.Duration
import javax.sql.DataSource

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import mu.KotlinLogging
import org.flywaydb.core.Flyway
import org.postgresql.ds.PGSimpleDataSource

import no.nav.sokos.utleggstrekk.config.PropertiesConfig
import no.nav.sokos.utleggstrekk.config.PropertiesConfig.postgresConfig

object PostgresDataSource {
    private val logger = KotlinLogging.logger {}
    val dataSource: DataSource by lazy {
        dataSource()
    }

    fun migrate(dataSource: DataSource = dataSource()) {
        logger.info { "Flyway migration" }
        Flyway
            .configure()
            .dataSource(dataSource)
            .initSql("""SET ROLE "${postgresConfig.user}"""")
            .lockRetryCount(-1)
            .validateMigrationNaming(true)
            .load()
            .migrate()
            .migrationsExecuted
        logger.info { "Migration finished" }
    }

    private fun dataSource(hikariConfig: HikariConfig = hikariConfig()): HikariDataSource = HikariDataSource(hikariConfig)

    private fun hikariConfig(): HikariConfig =
        HikariConfig().apply {
            maximumPoolSize = 5
            minimumIdle = 1
            isAutoCommit = false

            if (PropertiesConfig.isLocal && postgresConfig.jdbcUrl.isEmpty()) {
                dataSource =
                    PGSimpleDataSource().apply {
                        password = postgresConfig.password
                        portNumbers = intArrayOf(postgresConfig.port.toInt())
                        serverNames = arrayOf(postgresConfig.host)
                        user = postgresConfig.username
                        databaseName = postgresConfig.name
                    }
            } else {
                jdbcUrl = postgresConfig.jdbcUrl
            }
            connectionTimeout = Duration.ofSeconds(10).toMillis()
            maxLifetime = Duration.ofMinutes(30).toMillis()
            initializationFailTimeout = Duration.ofMinutes(30).toMillis()
        }
}
