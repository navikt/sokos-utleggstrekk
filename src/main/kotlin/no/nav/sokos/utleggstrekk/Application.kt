package no.nav.sokos.utleggstrekk

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

import no.nav.sokos.utleggstrekk.config.ApplicationState
import no.nav.sokos.utleggstrekk.config.PropertiesConfig
import no.nav.sokos.utleggstrekk.config.PropertiesConfig.applicationProperties
import no.nav.sokos.utleggstrekk.config.PropertiesConfigOld
import no.nav.sokos.utleggstrekk.config.applicationLifecycleConfig
import no.nav.sokos.utleggstrekk.config.commonConfig
import no.nav.sokos.utleggstrekk.config.mergeWithEnv
import no.nav.sokos.utleggstrekk.config.routingConfig
import no.nav.sokos.utleggstrekk.database.PostgresDataSource
import no.nav.sokos.utleggstrekk.scheduling.UtleggstrekkScheduler
import no.nav.sokos.utleggstrekk.service.UtleggsTrekkService

fun main() {
    embeddedServer(Netty, port = 8080, module = Application::module).start(true)
}

private fun Application.module() {
    PropertiesConfig.load(environment.config.mergeWithEnv())

    val applicationState = ApplicationState()
    val utleggsTrekkService = UtleggsTrekkService()
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    applicationLifecycleConfig(applicationState)
    commonConfig()
    routingConfig(applicationState)

    if (!PropertiesConfig.isLocal) {
        PostgresDataSource.migrate()
    }

    utleggsTrekkService.calculateMetrics()

    val schedulerProperties = applicationProperties.configuration.scheduler
    if (schedulerProperties.isActive) {
        UtleggstrekkScheduler(appScope).scheduleHourlyAt(schedulerProperties.minutes) { utleggsTrekkService.schedule() }
        UtleggstrekkScheduler(appScope).scheduleDailyAt(hour = 8, minute = 0) { utleggsTrekkService.reportMissingKvittering() }
    } else {
        log.info("Property SCHEDULER_ACTIVE is false. Scheduler is not running.")
    }
}

// TODO: delete
private fun Application.moduleOld() {
    val applicationState = ApplicationState()
    val utleggsTrekkService = UtleggsTrekkService()
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    commonConfig()
    applicationLifecycleConfig(applicationState)
    routingConfig(applicationState)

    if (!PropertiesConfig.isLocal) {
        PostgresDataSource.migrate()
    }

    utleggsTrekkService.calculateMetrics()

    val schedulerActive = PropertiesConfigOld.getOrEmpty("SCHEDULER_ACTIVE").toBoolean()
    if (schedulerActive) {
        val minutes = (PropertiesConfigOld.getOrNull("SCHEDULER_MINUTES") ?: "45").toInt()
        UtleggstrekkScheduler(appScope).scheduleHourlyAt(minutes, name = "Utleggstrekk") { utleggsTrekkService.schedule() }
        UtleggstrekkScheduler(appScope).scheduleDailyAt(hour = 8, minute = 0, name = "Kvitteringssjekk") { utleggsTrekkService.reportMissingKvittering() }
    } else {
        log.info("Property SCHEDULER_ACTIVE is '$schedulerActive'. Scheduler is not running.")
    }
}