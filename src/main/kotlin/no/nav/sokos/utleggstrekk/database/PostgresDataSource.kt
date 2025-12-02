package no.nav.sokos.utleggstrekk.database

import java.time.Duration

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import mu.KotlinLogging
import org.flywaydb.core.Flyway
import org.postgresql.ds.PGSimpleDataSource

import no.nav.sokos.utleggstrekk.config.PropertiesConfig
import no.nav.vault.jdbc.hikaricp.HikariCPVaultUtil.createHikariDataSourceWithVaultIntegration

object PostgresDataSource {
    private val logger = KotlinLogging.logger {}
    val dataSource: HikariDataSource by lazy {
        dataSource()
    }

    fun migrate(dataSource: HikariDataSource = dataSource(role = PropertiesConfig.PostgresConfig.adminUser)) {
        logger.info { "Flyway migration" }
        Flyway
            .configure()
            .dataSource(dataSource)
            .initSql("""SET ROLE "${ PropertiesConfig.PostgresConfig.adminUser}"""")
            .lockRetryCount(-1)
            .validateMigrationNaming(true)
            .load()
            .migrate()
            .migrationsExecuted
        logger.info { "Migration finished" }
    }

    private fun dataSource(hikariConfig: HikariConfig = hikariConfig(), role: String = PropertiesConfig.PostgresConfig.user): HikariDataSource =
        if (PropertiesConfig.isLocal) {
            HikariDataSource(hikariConfig)
        } else {
            createHikariDataSourceWithVaultIntegration(
                hikariConfig,
                PropertiesConfig.PostgresConfig.vaultMountPath,
                role,
            )
        }

    private fun hikariConfig(): HikariConfig {
        val postgresConfig = PropertiesConfig.PostgresConfig
        return HikariConfig().apply {
            maximumPoolSize = 5
            minimumIdle = 1
            isAutoCommit = false
            dataSource =
                PGSimpleDataSource().apply {
                    if (PropertiesConfig.isLocal) {
                        password = postgresConfig.password
                        portNumbers = intArrayOf(postgresConfig.port.toInt())
                    } else {
                        jdbcUrl = postgresConfig.jdbcUrl
                    }
                    serverNames = arrayOf(postgresConfig.host)
                    user = postgresConfig.username
                    databaseName = postgresConfig.name
                    connectionTimeout = Duration.ofSeconds(10).toMillis()
                    maxLifetime = Duration.ofMinutes(30).toMillis()
                    initializationFailTimeout = Duration.ofMinutes(30).toMillis()
                }
        }
    }
}
