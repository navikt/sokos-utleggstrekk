package sokos.utleggstrekk

import io.ktor.server.application.*
import io.ktor.server.routing.*
import sokos.utleggstrekk.api.utleggstrekkApi

fun Application.routingConfig(
) {
	routing {
		utleggstrekkApi()
	}
}