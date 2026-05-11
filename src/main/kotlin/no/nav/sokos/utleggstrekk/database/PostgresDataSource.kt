package no.nav.sokos.utleggstrekk.database

import java.time.Duration

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import mu.KotlinLogging
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.callback.BaseCallback
import org.flywaydb.core.api.callback.Context
import org.flywaydb.core.api.callback.Event
import org.postgresql.ds.PGSimpleDataSource

import no.nav.sokos.utleggstrekk.config.PropertiesConfig
import no.nav.sokos.utleggstrekk.config.PropertiesConfig.postgresConfig

object PostgresDataSource {
    private val logger = KotlinLogging.logger {}
    val dataSource: HikariDataSource by lazy {
        dataSource()
    }

    fun migrate(dataSource: HikariDataSource = dataSource(role = postgresConfig.user)) {
        logger.info { "Flyway migration" }
        Flyway
            .configure()
            .dataSource(dataSource)
            .callbacks(SetRoleCallback(postgresConfig.user))
            .lockRetryCount(-1)
            .validateMigrationNaming(true)
            .load()
            .migrate()
            .migrationsExecuted
        logger.info { "Migration finished" }
    }

    private fun dataSource(hikariConfig: HikariConfig = hikariConfig(), role: String = postgresConfig.user): HikariDataSource = HikariDataSource(hikariConfig)

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

class SetRoleCallback(private val role: String) : BaseCallback() {
    override fun supports(event: Event, context: Context?): Boolean = event == Event.AFTER_CONNECT

    override fun handle(event: Event?, context: Context?) {
        if (event == Event.AFTER_CONNECT) {
            // low risk, but keeps the alarms from going off.
            val sanitizedRole = role.replace(Regex("[^a-zA-Z0-9_-]"), "")
            context?.connection?.createStatement()?.use { statement ->
                statement.execute("""SET ROLE "$sanitizedRole"""")
            }
        }
    }
}
