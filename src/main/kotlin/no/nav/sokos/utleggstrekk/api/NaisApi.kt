package no.nav.sokos.utleggstrekk.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Routing.naisApi(alive: () -> Boolean, ready: () -> Boolean) {
    route("internal") {
        get("isAlive") {
            when (alive()) {
                true -> call.respondText { "Application is alive" }
                else -> call.respondText(
                    text = "Application is not alive",
                    status = HttpStatusCode.InternalServerError
                )
            }
        }
        get("isReady") {
            when (ready()) {
                true -> call.respondText { "Application is ready" }
                else -> call.respondText(
                    text = "Application is not ready",
                    status = HttpStatusCode.InternalServerError
                )
            }
        }
    }
}