package sokos.utleggstrekk.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import sokos.utleggstrekk.config.PropertiesConfig
import java.sql.Connection

class OracleDataSource(
    private val databaseConfig: PropertiesConfig.DatabaseConfig = PropertiesConfig.DatabaseConfig()
) {
    private val dataSource: HikariDataSource = HikariDataSource(hikariConfig())
    val connection: Connection get() = dataSource.connection
    fun close() = dataSource.close()

    private fun hikariConfig() = HikariConfig().apply {
        driverClassName = "oracle.jdbc.OracleDriver"
        poolName = "OSESKATT_POOL"
        schema = databaseConfig.schema
        jdbcUrl = databaseConfig.jdbcUrl
        username = databaseConfig.username
        password = databaseConfig.password
        maximumPoolSize = 3
        isAutoCommit = true
    }
}