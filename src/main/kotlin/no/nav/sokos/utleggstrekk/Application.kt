package no.nav.sokos.utleggstrekk

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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
import no.nav.sokos.utleggstrekk.config.PropertiesConfig
import no.nav.sokos.utleggstrekk.config.commonConfig
import no.nav.sokos.utleggstrekk.database.PostgresDataSource
import no.nav.sokos.utleggstrekk.domene.nav.scheduling.UtleggstrekkScheduler
import no.nav.sokos.utleggstrekk.service.UtleggsTrekkService

fun main() {
    embeddedServer(Netty, port = 8080, module = Application::module).start(true)
}

private fun Application.module() {
    val applicationState = ApplicationState()
    val azureConfiguration = AzureConfiguration()
    val utleggsTrekkService = UtleggsTrekkService()
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    commonConfig(azureConfiguration)
    applicationLifecycleConfig(applicationState)
    routing {
        internalNaisRoutes(applicationState)
        utleggstrekkApi(utleggsTrekkService)
    }

    if (!PropertiesConfig.isLocal) {
        PostgresDataSource.migrate()
    }

    utleggsTrekkService.calculateMetrics()

    val schedulerActive = PropertiesConfig.getOrEmpty("SCHEDULER_ACTIVE")
    if (schedulerActive == "true") {
        val minutes = (PropertiesConfig.getOrNull("SCHEDULER_MINUTES") ?: "55").toInt()
        UtleggstrekkScheduler(appScope).scheduleHourlyAt(minutes) { utleggsTrekkService.schedule() }
        UtleggstrekkScheduler(appScope).scheduleDailyAt(hour = 8, minute = 0) { }
    } else {
        log.info("Property SCHEDULER_ACTIVE is '$schedulerActive'. Scheduler is not running.")
    }
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