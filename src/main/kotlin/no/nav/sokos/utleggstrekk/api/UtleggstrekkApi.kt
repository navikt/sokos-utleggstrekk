package no.nav.sokos.utleggstrekk.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import mu.KotlinLogging
import no.nav.sokos.utleggstrekk.service.UtleggstrekkService
import kotlin.reflect.jvm.internal.impl.descriptors.Visibilities.Private

fun Routing.utleggstrekkApi(
    utleggstrekkService: UtleggstrekkService
) {
    val logger = KotlinLogging.logger { }

    route("utleggstrekk/") {
        get("hei") {
            call.respond(HttpStatusCode.OK, "Hellu")
        }

        get("hent") {
            try {
                utleggstrekkService.hentAlle()
                call.respond(HttpStatusCode.OK, "tja gikk bra")
            }catch (e: Exception){
                println(e.toString())
                call.respond(HttpStatusCode.Conflict, "Gikk ikke så bra ${e.message}")
            }
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


