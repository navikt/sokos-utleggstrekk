package sokos.utleggstrekk.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Routing.naisApi(alive: () -> Boolean, ready: () -> Boolean) {
    route("internal") {
        get("is_alive") {
            when (alive()) {
                true -> call.respondText { "Application is alive" }
                else -> call.respondText(
                    text = "Application is not alive",
                    status = HttpStatusCode.InternalServerError
                )
            }
        }
        get("is_ready") {
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