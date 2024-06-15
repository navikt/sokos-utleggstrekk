package sokos.utleggstrekk

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.stop
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import no.nav.utleggstrekk.config.AzureConfiguration
import sokos.utleggstrekk.api.naisApi
import sokos.utleggstrekk.api.utleggstrekkApi
import sokos.utleggstrekk.config.commonConfig
import java.util.concurrent.TimeUnit
import kotlin.properties.Delegates

fun main() {
    val applicationState = HttpServer.ApplicationState()
    val configuration = AzureConfiguration()

    applicationState.ready = true
    HttpServer(applicationState).start()

}

class HttpServer(
    private val applicationState: ApplicationState,
    port: Int = 8080,
) {
    init {
        Runtime.getRuntime().addShutdownHook(Thread {
            applicationState.alive = false
            this.embeddedServer.stop(gracePeriod = 2, timeout = 20, TimeUnit.SECONDS)
        })
    }

    private val embeddedServer = embeddedServer(Netty, port) {
        commonConfig()
        routing {
            naisApi({ applicationState.ready }, { applicationState.alive })
//            swaggerApi()
            utleggstrekkApi()
        }
    }

    fun start() {
        applicationState.alive = true
        embeddedServer.start(wait = true)
    }

    fun Application.applicationLifecycleConfig(applicationState: ApplicationState) {
        environment.monitor.subscribe(ApplicationStarted) {
            applicationState.ready = true
        }

        environment.monitor.subscribe(ApplicationStopped) {
            applicationState.ready = false
        }
    }

    class ApplicationState {
        var ready: Boolean by Delegates.observable(false) { _, _, newValue ->
//            if (!newValue) Metrics.appStateReadyFalse.inc()
        }

        var alive: Boolean by Delegates.observable(false) { _, _, newValue ->
//            if (!newValue) Metrics.appStateRunningFalse.inc()
        }
    }
}

