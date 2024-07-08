package no.nav.sokos.utleggstrekk.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import mu.KotlinLogging
import no.nav.sokos.utleggstrekk.config.PropertiesConfig
import no.nav.vault.jdbc.hikaricp.HikariCPVaultUtil.createHikariDataSourceWithVaultIntegration
import java.sql.Connection
import org.flywaydb.core.Flyway


class PostgresDataSource() {
    private val logger = KotlinLogging.logger { }
    private val postgresConfig: PropertiesConfig.PostgresConfig = PropertiesConfig.PostgresConfig()
    private val isLocal = PropertiesConfig.Configuration().profile == PropertiesConfig.Profile.LOCAL
    private var dataSource: HikariDataSource
    private val adminRole = "${postgresConfig.name}-admin"
    private val userRole = "${postgresConfig.name}-user"
    val connection: Connection get() = dataSource.connection.apply { autoCommit = false }


    init{
        if (!isLocal) {
            val role = adminRole
            logger.info("Flyway db opprettes med rolle $role")
            Flyway.configure()
                .dataSource(dataSource(role))
                .initSql("""SET ROLE "$role"""")
                .load()
                .migrate()
        }
       dataSource = dataSource()
    }

    private fun dataSource(role: String = userRole) =
        if ( PropertiesConfig.isLocal() ) HikariDataSource(hikariConfig()) else createHikariDataSourceWithVaultIntegration(
            hikariConfig(),
            postgresConfig.vaultMountPath,
            role
        )


    private fun hikariConfig() = HikariConfig().apply {
        minimumIdle = 1
        maxLifetime = 30000
        maximumPoolSize = 4
        connectionTimeout = 10000
        isAutoCommit = false
        idleTimeout = 10000
        //connectionTestQuery = "SELECT * FROM ${dbConfig.testTable} LIMIT 1"
        jdbcUrl = postgresConfig.jdbcUrl
        transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        validate()
        if (isLocal) {
            username = postgresConfig.username
            password = postgresConfig.password
        }
    }
}
