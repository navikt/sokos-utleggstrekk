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
import no.nav.sokos.utleggstrekk.mq.ShowAllQueueDepth
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
            val utleggstrekk = utleggstrekkService.behandleUtleggstrekk()
            println("antall elementer: ${utleggstrekk.size}")
            call.respond(HttpStatusCode.OK, utleggstrekk.toString())
        }
        get("hent/{sekvensnr}") {
            val sekvensnr = call.parameters["sekvensnr"]
            if (sekvensnr.isNullOrBlank() || sekvensnr.toInt() < 0) {
                call.respond(HttpStatusCode.BadRequest, "Ugyldig sekvensnr")
            } else {
                val utleggstrekk = utleggstrekkService.hentUtleggstrekkFraSekvensnrOgLagreAlleNye(sekvensnr.toInt())
                println("Antall mottat på søk fra sekvensnr $sekvensnr: ${utleggstrekk.size}")
                call.respond(HttpStatusCode.OK, utleggstrekk.toString())
            }
        }
        get("hentnye}") {
            val utleggstrekk = utleggstrekkService.hentAlleNyeUtleggstrekk()
            println("Antall Nye: ${utleggstrekk.size}")
            call.respond(HttpStatusCode.OK, utleggstrekk.toString())
        }
    }
    route("mq/"){
        get("a") {
            val mq = ShowAllQueueDepth()
            val res = mq.allLocalQueueDepths()
            call.respond(HttpStatusCode.OK, res.joinToString("\n"))
        }
        get("a/{namePart}"){
            val namePart = call.parameters["namePart"]
            val mq = ShowAllQueueDepth()
            val res = mq.allLocalQueueDepths(namePart!!)
            call.respond(HttpStatusCode.OK, res.joinToString("\n"))
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