package no.nav.sokos.utleggstrekk.util

import io.kotest.extensions.testcontainers.toDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.ext.ScriptUtils
import org.testcontainers.jdbc.JdbcDatabaseDelegate
import java.util.Locale
import java.util.UUID

class TestContainer(private val name: String = UUID.randomUUID().toString()) {
    fun startContainer(script: String = "", initScripts: List<String> = emptyList()) =
        createContainer(script, initScripts).toDataSource {
            maximumPoolSize = 100
            minimumIdle = 1
            isAutoCommit = false
        }

    private fun createContainer(script: String, scripts: List<String> = emptyList()): PostgreSQLContainer<Nothing> {
        val container =
            PostgreSQLContainer<Nothing>("postgres:latest").apply {
                withCreateContainerCmdModifier { cmd ->
                    cmd.withName(
                        name.lowercase(Locale.getDefault())
                            .replace(' ', '-')
                            .replace('æ', 'e')
                            .replace('ø', 'o')
                            .replace('å', 'a')
                            .replace(',', '-'),
                    )
                }
                withReuse(false)
                start()
            }

        if (scripts.isNotEmpty()) {
            scripts.forEach {
                ScriptUtils.runInitScript(JdbcDatabaseDelegate(container, ""), it)
            }
        }
        if (script.isNotBlank()) ScriptUtils.runInitScript(JdbcDatabaseDelegate(container, ""), script)

        return container
    }
}