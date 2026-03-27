package no.nav.sokos.utleggstrekk.listener

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.kotest.core.listeners.TestListener
import io.kotest.core.spec.Spec
import io.ktor.server.config.ApplicationConfig
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotliquery.queryOf
import org.testcontainers.containers.JdbcDatabaseContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.ext.ScriptUtils
import org.testcontainers.jdbc.JdbcDatabaseDelegate
import org.testcontainers.utility.DockerImageName

import no.nav.sokos.utleggstrekk.config.PropertiesConfig
import no.nav.sokos.utleggstrekk.config.PropertiesConfig.postgresConfig
import no.nav.sokos.utleggstrekk.database.PostgresDataSource
import no.nav.sokos.utleggstrekk.database.Repository
import no.nav.sokos.utleggstrekk.database.withTransaction

object DBListener : TestListener {
    private val dockerImageName = "postgres:latest"
    private val container by lazy {
        PostgreSQLContainer<Nothing>(DockerImageName.parse(dockerImageName)).apply {
            withReuse(false)
            withUsername(postgresConfig.user)
            waitingFor(Wait.defaultWaitStrategy())
            start()
        }
    }

    val dataSource: HikariDataSource by lazy {
        container
            .toDataSource {
                maximumPoolSize = 100
                minimumIdle = 1
                isAutoCommit = false
            }.apply {
                PostgresDataSource.migrate(this)
            }
    }

    val repository by lazy {
        Repository(dataSource)
    }

    override suspend fun beforeSpec(spec: Spec) {
        super.beforeSpec(spec)

        mockkObject(PropertiesConfig)
        every { PropertiesConfig.config } returns ApplicationConfig("application-test.conf")
        dataSource
    }

    fun loadInitScript(name: String) = ScriptUtils.runInitScript(JdbcDatabaseDelegate(container, ""), name)

    fun clearDB() {
        dataSource.withTransaction { session ->
            val tables = mutableListOf<String>()
            // Collect all public tables except Flyway history
            session.list(
                queryOf("SELECT tablename FROM pg_tables WHERE schemaname='public' AND tablename <> 'flyway_schema_history'"),
            ) { rs -> tables += rs.string("tablename") }

            if (tables.isNotEmpty()) {
                session.execute(queryOf("TRUNCATE TABLE ${tables.joinToString(", ")} RESTART IDENTITY CASCADE"))
            }
        }
    }

    override suspend fun afterSpec(spec: Spec) {
        clearDB()
        clearAllMocks()
        unmockkObject(PropertiesConfig)
    }

    fun JdbcDatabaseContainer<*>.toDataSource(configure: HikariConfig.() -> Unit = {}): HikariDataSource {
        val config = HikariConfig()
        config.jdbcUrl = jdbcUrl
        config.username = username
        config.password = password
        config.minimumIdle = 0
        config.configure()
        return HikariDataSource(config)
    }
}