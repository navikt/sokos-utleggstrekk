package no.nav.sokos.utleggstrekk.database

import java.time.Duration

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import mu.KotlinLogging
import org.flywaydb.core.Flyway
import org.postgresql.ds.PGSimpleDataSource

import no.nav.sokos.utleggstrekk.config.PropertiesConfigOld

object PostgresDataSource {
    private val logger = KotlinLogging.logger {}
    val dataSource: HikariDataSource by lazy {
        dataSource()
    }

    fun migrate(dataSource: HikariDataSource = dataSource(role = PropertiesConfigOld.PostgresConfig.user)) {
        logger.info { "Flyway migration" }
        Flyway
            .configure()
            .dataSource(dataSource)
            .initSql("""SET ROLE "${PropertiesConfigOld.PostgresConfig.user}"""")
            .lockRetryCount(-1)
            .validateMigrationNaming(true)
            .load()
            .migrate()
            .migrationsExecuted
        logger.info { "Migration finished" }
    }

    private fun dataSource(hikariConfig: HikariConfig = hikariConfig(), role: String = PropertiesConfigOld.PostgresConfig.user): HikariDataSource = HikariDataSource(hikariConfig)

    private fun hikariConfig(): HikariConfig {
        val postgresConfig = PropertiesConfigOld.PostgresConfig
        return HikariConfig().apply {
            maximumPoolSize = 5
            minimumIdle = 1
            isAutoCommit = false

            if (PropertiesConfigOld.isLocal && postgresConfig.jdbcUrl.isEmpty()) {
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
}