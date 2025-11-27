package no.nav.sokos.utleggstrekk

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.ServerReady
import io.ktor.server.application.log
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing

import no.nav.sokos.utleggstrekk.api.internalNaisRoutes
import no.nav.sokos.utleggstrekk.api.utleggstrekkApi
import no.nav.sokos.utleggstrekk.config.AzureConfiguration
import no.nav.sokos.utleggstrekk.config.commonConfig
import no.nav.sokos.utleggstrekk.database.PostgresDataSource
import no.nav.sokos.utleggstrekk.service.UtleggsTrekkService

fun main() {
    embeddedServer(Netty, port = 8080, module = Application::module).start(true)
}

private fun Application.module() {
    val applicationState = ApplicationState()
    val azureConfiguration = AzureConfiguration()

    commonConfig(azureConfiguration)
    applicationLifecycleConfig(applicationState)
    routing {
        internalNaisRoutes(applicationState)
        utleggstrekkApi(UtleggsTrekkService())
    }

//    if (!PropertiesConfig.isLocal) {
    PostgresDataSource.migrate()
//    }
}

fun Application.applicationLifecycleConfig(applicationState: ApplicationState) {
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
        it.log.info("Application is stopped")
    }
}

class ApplicationState(
    var ready: Boolean = false,
    var alive: Boolean = false,
)
