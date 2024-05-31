package no.nav.utleggstrekk.api

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Routing.utleggstrekkApi() {
    route("/utleggstrekk") {
        get("hent") {
            call.respondText("hellu")
        }
    }
}
