package nav.no.sokos.utleggstrekk.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import mu.KotlinLogging

fun Routing.utleggstrekkApi() {
    val logger = KotlinLogging.logger { }

    route("utleggstrekk/") {
        get("hei") {
            call.respond(HttpStatusCode.OK, "Hellu")
        }

    }
}

private fun Route.authenticate(
    useAuthentication: Boolean,
    block: Route.() -> Unit
) {
    if (useAuthentication) {
        authenticate { block() }
    } else {
        block()
    }
}


