package sokos.utleggstrekk

import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.stop
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import sokos.utleggstrekk.api.naisApi
import sokos.utleggstrekk.api.utleggstrekkApi
import sokos.utleggstrekk.config.AzureConfiguration
import sokos.utleggstrekk.config.commonConfig
import sokos.utleggstrekk.database.OracleDataSource
import sokos.utleggstrekk.metrics.Metrics
import sokos.utleggstrekk.service.DatabaseService
import sokos.utleggstrekk.service.UtleggstrekkService
import java.util.concurrent.TimeUnit
import kotlin.properties.Delegates


fun main() {
    val applicationState = ApplicationState()
    val dataSource = OracleDataSource()
    val databaseService = DatabaseService(dataSource)
    val utleggstrekkService = UtleggstrekkService(databaseService)
    val configuration = AzureConfiguration()

    applicationState.ready = true
    HttpServer(applicationState, utleggstrekkService, dataSource, configuration).start()

}

class HttpServer(
    private val applicationState: ApplicationState,
    private val utleggstrekkService: UtleggstrekkService,
    private val dataSource: OracleDataSource,
    private val azureConfiguration: AzureConfiguration,
    port: Int = 8080,
) {
    init {
        Runtime.getRuntime().addShutdownHook(Thread {
            applicationState.running = false
            dataSource.close()
            this.embeddedServer.stop(gracePeriod = 2, timeout = 20, TimeUnit.SECONDS)
        })
    }

    private val embeddedServer = embeddedServer(Netty, port) {
        commonConfig(azureConfiguration)
        routing {
            naisApi({ applicationState.ready }, { applicationState.running })
            utleggstrekkApi()
        }
    }

    fun start() {
        applicationState.running = true
        embeddedServer.start(wait = true)
    }
}

class ApplicationState {
    var ready: Boolean by Delegates.observable(false) { _, _, newValue ->
        if (!newValue) Metrics.appStateReadyFalse.inc()
    }

    var running: Boolean by Delegates.observable(false) { _, _, newValue ->
        if (!newValue) Metrics.appStateRunningFalse.inc()
    }
}


