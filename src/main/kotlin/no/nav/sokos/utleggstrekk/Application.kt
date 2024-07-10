package no.nav.sokos.utleggstrekk

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import no.nav.sokos.utleggstrekk.api.internalNaisRoutes
import no.nav.sokos.utleggstrekk.api.utleggstrekkApi
import no.nav.sokos.utleggstrekk.config.AzureConfiguration
import no.nav.sokos.utleggstrekk.config.PropertiesConfig
import no.nav.sokos.utleggstrekk.config.commonConfig
import no.nav.sokos.utleggstrekk.database.PostgresDataSource
import no.nav.sokos.utleggstrekk.service.DatabaseService
import no.nav.sokos.utleggstrekk.service.UtleggstrekkService

fun main() {
    embeddedServer(Netty, port = 8080, module = Application::module).start(true)
}

private fun Application.module() {
    val applicationState = ApplicationState()
    val azureConfiguration = AzureConfiguration()
    val databaseService = DatabaseService()
    val utleggstrekkService = UtleggstrekkService(databaseService)

    commonConfig(azureConfiguration)
    applicationLifecycleConfig(applicationState)
    routing {
        internalNaisRoutes(applicationState)
        utleggstrekkApi(utleggstrekkService)
    }

    if (!PropertiesConfig.isLocal()) {
        PostgresDataSource.postgresMigrate()
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