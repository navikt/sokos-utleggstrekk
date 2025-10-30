package no.nav.sokos.utleggstrekk.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.route

import no.nav.sokos.utleggstrekk.service.UtleggsTrekkService

// TODO: Se på hva som er gjort i spk-mottak
// TODO: Se på sokos-krav mhp. autentisering
fun Routing.utleggstrekkApi(utleggsTrekkService: UtleggsTrekkService) {
    route("utleggstrekk") {
        get("hentNyeTrekk") {
            utleggsTrekkService.schedule()
            call.respond(HttpStatusCode.OK, "Sender trekk til OS")
        }
    }
}

private fun Route.authenticate(useAuthentication: Boolean, block: Route.() -> Unit) {
    if (useAuthentication) {
        authenticate { block() }
    } else {
        block()
    }
}
