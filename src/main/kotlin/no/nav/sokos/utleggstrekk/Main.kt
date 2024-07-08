package no.nav.sokos.utleggstrekk

import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.stop
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import no.nav.sokos.utleggstrekk.api.naisApi
import no.nav.sokos.utleggstrekk.api.utleggstrekkApi
import no.nav.sokos.utleggstrekk.config.AzureConfiguration
import no.nav.sokos.utleggstrekk.config.commonConfig
import no.nav.sokos.utleggstrekk.database.PostgresDataSource
import no.nav.sokos.utleggstrekk.service.DatabaseService
import no.nav.sokos.utleggstrekk.service.UtleggstrekkService
import java.util.concurrent.TimeUnit
import kotlin.properties.Delegates


fun main() {
    val applicationState = ApplicationState()
    val configuration = AzureConfiguration()
    val dataSource = PostgresDataSource()
    val databaseService = DatabaseService(dataSource)
    val utleggstrekkService = UtleggstrekkService(databaseService)

    applicationState.ready = true
    HttpServer(applicationState, databaseService, utleggstrekkService, configuration).start()

}

class HttpServer(
    private val applicationState: ApplicationState,
    private val databaseService: DatabaseService,
    private val utleggstrekkService: UtleggstrekkService,
    private val azureConfiguration: AzureConfiguration,
    port: Int = 8080,
) {
    init {
        Runtime.getRuntime().addShutdownHook(Thread {
            applicationState.running = false
            this.embeddedServer.stop(gracePeriod = 2, timeout = 20, TimeUnit.SECONDS)
        })
    }

    private val embeddedServer = embeddedServer(Netty, port) {
        commonConfig(azureConfiguration)
        routing {
            naisApi({ applicationState.ready }, { applicationState.running })
            utleggstrekkApi(utleggstrekkService)
        }
    }

    fun start() {
        applicationState.running = true
        embeddedServer.start(wait = true)
    }
}

class ApplicationState {
    var ready: Boolean by Delegates.observable(false) { _, _, _ ->
    }

    var running: Boolean by Delegates.observable(false) { _, _, _ ->
    }
}


