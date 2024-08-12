package no.nav.sokos.utleggstrekk.util

import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.PortBinding
import com.github.dockerjava.api.model.Ports
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.kotest.core.listeners.TestListener
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.extensions.testcontainers.toDataSource
import no.nav.sokos.utleggstrekk.config.PropertiesConfig
import no.nav.sokos.utleggstrekk.database.PostgresDataSource
import org.postgresql.ds.PGSimpleDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.ext.ScriptUtils
import org.testcontainers.jdbc.JdbcDatabaseDelegate
import java.time.Duration

object TestContainer : TestListener {
    private val properties = PropertiesConfig.PostgresConfig()

    private val container =
        PostgreSQLContainer<Nothing>("postgres:latest")
            .apply {
                withExposedPorts(5432)
                withUsername(properties.adminUser)
                withPassword(properties.password)
                withDatabaseName(properties.name)
                withCreateContainerCmdModifier { cmd ->
                    cmd.hostConfig!!.withPortBindings(PortBinding(Ports.Binding.bindPort(5432), ExposedPort(5432)))
                }
            }

    val dataSource: HikariDataSource by lazy {
        container.toDataSource {
            hikariConfig
        }
    }

    val hikariConfig =
        HikariConfig().apply {
            maximumPoolSize = 5
            minimumIdle = 1
            dataSource =
                PGSimpleDataSource().apply {
                    user = properties.adminUser
                    password = properties.password
                    serverNames = arrayOf(container.host)
                    databaseName = properties.name
                    portNumbers = intArrayOf(properties.port.toInt())
                    connectionTimeout = Duration.ofSeconds(10).toMillis()
                    maxLifetime = Duration.ofMinutes(30).toMillis()
                    initializationFailTimeout = Duration.ofMinutes(30).toMillis()
                }
        }

    fun loadInitScript(script: String) = ScriptUtils.runInitScript(JdbcDatabaseDelegate(container, ""), script)

    fun loadInitScripts(scripts: List<String>) = scripts.forEach { loadInitScript(it) }

    override suspend fun beforeTest(testCase: TestCase) {
        container.start()
        PostgresDataSource.migrate(dataSource)
    }

    override suspend fun afterTest(
        testCase: TestCase,
        result: TestResult,
    ) {
        container.stop()
    }
}