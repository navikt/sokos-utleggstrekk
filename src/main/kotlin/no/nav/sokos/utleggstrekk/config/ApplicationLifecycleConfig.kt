package no.nav.sokos.utleggstrekk.config

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.ServerReady
import io.ktor.server.application.log

fun Application.applicationLifecycleConfig(applicationState: ApplicationState, onShutdown: () -> Unit) {
    monitor.subscribe(ApplicationStarted) {
        applicationState.alive = true
        it.log.info("Application is started")
    }

    monitor.subscribe(ServerReady) {
        applicationState.ready = true
        it.log.info("Server is ready")
    }

    monitor.subscribe(ApplicationStopped) {
        applicationState.alive = false
        applicationState.ready = false
        onShutdown()
        it.log.info("Application is stopped")
    }
}

class ApplicationState(
    var ready: Boolean = false,
    var alive: Boolean = false,
)