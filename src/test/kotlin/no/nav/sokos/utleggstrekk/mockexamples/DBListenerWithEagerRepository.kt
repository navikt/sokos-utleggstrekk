package no.nav.sokos.utleggstrekk.mockexamples

import com.zaxxer.hikari.HikariDataSource
import io.kotest.core.listeners.TestListener
import io.kotest.core.spec.Spec
import io.kotest.extensions.testcontainers.toDataSource
import io.ktor.server.config.ApplicationConfig
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotliquery.queryOf
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.ext.ScriptUtils
import org.testcontainers.jdbc.JdbcDatabaseDelegate
import org.testcontainers.utility.DockerImageName

import no.nav.sokos.utleggstrekk.config.PropertiesConfig
import no.nav.sokos.utleggstrekk.database.PostgresDataSource
import no.nav.sokos.utleggstrekk.database.Repository
import no.nav.sokos.utleggstrekk.database.withTransaction

/*
 * Identical to DBListener, except beforeSpec also calls repository.deleteOldData().
 *
 * That single extra call is what forces the DBListener.repository lazy to initialise —
 * loading Repository.kt's class file and freezing its `private val logger` to whatever
 * KotlinLogging.logger {} returns at that moment (the real logger, since no
 * mockkObject(KotlinLogging) is active during beforeSpec).
 *
 * This makes RepositoryLoggingDemoTest fail for the correct reason even when run in
 * isolation: the val is guaranteed to be frozen before any test body runs, regardless
 * of whether another spec happened to access DBListener.repository first.
 */
object DBListenerWithEagerRepository : TestListener {
    private val dockerImageName = "postgres:latest"
    private val container by lazy {
        PostgreSQLContainer<Nothing>(DockerImageName.parse(dockerImageName)).apply {
            withReuse(false)
            withUsername(PropertiesConfig.postgresConfig.user)
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

        // This is the only difference from DBListener.
        // Accessing repository here triggers the lazy, loads Repository.kt's class, and
        // freezes `private val logger` to the real logger — before any test body runs
        // and before any mockkObject(KotlinLogging) can be installed.
        repository.deleteOldData()
    }

    fun loadInitScript(name: String) = ScriptUtils.runInitScript(JdbcDatabaseDelegate(container, ""), name)

    fun clearDB() {
        dataSource.withTransaction { session ->
            val tables = mutableListOf<String>()
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
}