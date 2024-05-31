package no.nav.utleggstrekk

import io.ktor.server.application.*
import io.ktor.server.routing.*
import no.nav.utleggstrekk.api.utleggstrekkApi

fun Application.routingConfig(
) {
	routing {
		utleggstrekkApi()
	}
}