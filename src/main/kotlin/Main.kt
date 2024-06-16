package nav.no.sokos.utleggstrekk

import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.stop
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import nav.no.sokos.utleggstrekk.api.naisApi
import nav.no.sokos.utleggstrekk.api.utleggstrekkApi
import nav.no.sokos.utleggstrekk.config.AzureConfiguration
import nav.no.sokos.utleggstrekk.config.commonConfig
import java.util.concurrent.TimeUnit
import kotlin.properties.Delegates


fun main() {
    val applicationState = ApplicationState()
    val configuration = AzureConfiguration()

    applicationState.ready = true
    HttpServer(applicationState, configuration).start()

}

class HttpServer(
    private val applicationState: ApplicationState,
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
            utleggstrekkApi()
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


