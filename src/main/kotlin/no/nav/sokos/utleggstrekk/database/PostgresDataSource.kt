package no.nav.sokos.utleggstrekk.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import mu.KotlinLogging
import no.nav.sokos.utleggstrekk.config.PropertiesConfig
import no.nav.vault.jdbc.hikaricp.HikariCPVaultUtil.createHikariDataSourceWithVaultIntegration
import org.flywaydb.core.Flyway
import org.postgresql.ds.PGSimpleDataSource
import java.time.Duration

object PostgresDataSource {
    private val logger = KotlinLogging.logger {}

    fun migrate(dataSource: HikariDataSource = dataSource(role = PropertiesConfig.PostgresConfig().adminUser)) {
        println("migratign with ${dataSource.jdbcUrl}")
        logger.info { "Flyway migration" }
        Flyway
            .configure()
            .dataSource(dataSource)
            .initSql("""SET ROLE "${ PropertiesConfig.PostgresConfig().adminUser}"""")
            .lockRetryCount(-1)
            .validateMigrationNaming(true)
            .load()
            .migrate()
            .migrationsExecuted
        logger.info { "Migration finished" }
    }

    fun dataSource(
        hikariConfig: HikariConfig = hikariConfig(),
        role: String = PropertiesConfig.PostgresConfig().user,
    ): HikariDataSource =
        if (PropertiesConfig.isLocal()) {
            HikariDataSource(hikariConfig)
        } else {
            createHikariDataSourceWithVaultIntegration(
                hikariConfig,
                PropertiesConfig.PostgresConfig().vaultMountPath,
                role,
            )
        }

    private fun hikariConfig(): HikariConfig {
        val postgresConfig = PropertiesConfig.PostgresConfig()
        return HikariConfig().apply {
            maximumPoolSize = 5
            minimumIdle = 1
            isAutoCommit = false
            dataSource =
                PGSimpleDataSource().apply {
                    if (PropertiesConfig.isLocal()) {
                        user = postgresConfig.username
                        password = postgresConfig.password
                    }
                    serverNames = arrayOf(postgresConfig.hostName)
                    databaseName = postgresConfig.name
                    portNumbers = intArrayOf(postgresConfig.port.toInt())
                    connectionTimeout = Duration.ofSeconds(10).toMillis()
                    maxLifetime = Duration.ofMinutes(30).toMillis()
                    initializationFailTimeout = Duration.ofMinutes(30).toMillis()
                }
        }
    }
}