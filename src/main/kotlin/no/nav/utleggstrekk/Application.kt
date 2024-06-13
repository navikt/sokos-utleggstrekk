package sokos.utleggstrekk

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import sokos.utleggstrekk.config.commonConfig
import sokos.utleggstrekk.config.internalNaisRoutes

fun main() {
    embeddedServer(Netty, port = 8080, module = Application::module).start(true)
}

fun Application.module() {
    val applicationState = ApplicationState()

    commonConfig()
    applicationLifecycleConfig(applicationState)
    routing {
        internalNaisRoutes(applicationState)
    }
}

fun Application.applicationLifecycleConfig(applicationState: ApplicationState) {
    environment.monitor.subscribe(ApplicationStarted) {
        applicationState.ready = true
    }

    environment.monitor.subscribe(ApplicationStopped) {
        applicationState.ready = false
    }
}

class ApplicationState(
    var ready: Boolean = true,
    var alive: Boolean = true,
)

