package no.nav.sokos.utleggstrekk.config

import io.ktor.server.application.Application
import io.ktor.server.routing.routing

import no.nav.sokos.utleggstrekk.ApplicationState
import no.nav.sokos.utleggstrekk.api.internalNaisRoutes

fun Application.routingConfig(applicationState: ApplicationState) {
    routing {
        internalNaisRoutes(applicationState)
    }
}