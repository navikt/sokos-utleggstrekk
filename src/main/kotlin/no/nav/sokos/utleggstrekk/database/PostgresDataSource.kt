package no.nav.sokos.utleggstrekk.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import mu.KotlinLogging
import no.nav.sokos.utleggstrekk.config.PropertiesConfig
import no.nav.vault.jdbc.hikaricp.HikariCPVaultUtil.createHikariDataSourceWithVaultIntegration
import org.flywaydb.core.Flyway

object PostgresDataSource {
    private val postgresConfig: PropertiesConfig.PostgresConfig = PropertiesConfig.PostgresConfig()
    private val logger = KotlinLogging.logger {}

    fun postgresMigrate(dataSource: HikariDataSource = dataSource(postgresConfig.adminUser)) {
        logger.info { "Flyway migration" }
        Flyway
            .configure()
            .dataSource(dataSource)
            .initSql("""SET ROLE "${ postgresConfig.adminUser}"""")
            .lockRetryCount(-1)
            .validateMigrationNaming(true)
            .load()
            .migrate()
            .migrationsExecuted
        logger.info { "Migration finished" }
    }

    fun dataSource(role: String = postgresConfig.user): HikariDataSource =
        if (PropertiesConfig.isLocal()) {
            HikariDataSource(hikariConfig())
        } else {
            createHikariDataSourceWithVaultIntegration(
                hikariConfig(),
                postgresConfig.vaultMountPath,
                role,
            )
        }

    private fun hikariConfig() =
        HikariConfig().apply {
            minimumIdle = 1
            maxLifetime = 30000
            maximumPoolSize = 4
            connectionTimeout = 10000
            isAutoCommit = false
            idleTimeout = 10000
            jdbcUrl = postgresConfig.jdbcUrl
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
            if (PropertiesConfig.isLocal()) {
                username = postgresConfig.username
                password = postgresConfig.password
            }
        }
}