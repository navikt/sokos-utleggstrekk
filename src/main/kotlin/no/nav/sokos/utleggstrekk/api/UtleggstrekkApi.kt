package no.nav.sokos.utleggstrekk.api

import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import mu.KotlinLogging
import no.nav.sokos.utleggstrekk.domene.ske.Utleggstrekk
import no.nav.sokos.utleggstrekk.service.UtleggstrekkService

fun Routing.utleggstrekkApi(
    utleggstrekkService: UtleggstrekkService,
) {
    val logger = KotlinLogging.logger { }

    route("utleggstrekk/") {
        get("hei") {
            call.respond(HttpStatusCode.OK, "Hellu")
        }

        get("hent") {
            val utleggstrekk = utleggstrekkService.hentAlleUtleggstrekk()
            println("antall elementer: ${utleggstrekk.size}")
            call.respond(HttpStatusCode.OK, utleggstrekk.toString())
        }
        get("hent/{sekvensnr}") {
            val sekvensnr = call.parameters["sekvensnr"]
            if (sekvensnr.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Ugyldig sekvensnr")
            } else {
                val utleggstrekk = utleggstrekkService.hentUtleggstrekkFraSekvensnrOgLagreAlleNye(sekvensnr.toInt())
                call.respond(HttpStatusCode.OK, utleggstrekk.toString())
            }
        }
    }
}

private fun Route.authenticate(
    useAuthentication: Boolean,
    block: Route.() -> Unit,
) {
    if (useAuthentication) {
        authenticate { block() }
    } else {
        block()
    }
}