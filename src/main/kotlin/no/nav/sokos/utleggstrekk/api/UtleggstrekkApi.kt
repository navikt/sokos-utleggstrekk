package no.nav.sokos.utleggstrekk.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.route

import no.nav.sokos.utleggstrekk.service.UtleggstrekkService

fun Routing.utleggstrekkApi(utleggstrekkService: UtleggstrekkService = UtleggstrekkService()) {
    route("utleggstrekk") {
        get("hentAlleFullPakke") {
            utleggstrekkService.hentOgSendUtleggstrekk()
            call.respond(HttpStatusCode.OK, "Trekk sendt")
        }

        get("hentnye") {
            utleggstrekkService.hentOgLagreNyeUtleggstrekk()
            call.respond(HttpStatusCode.OK)
        }

        get("hent/{sekvensnr}") {
            val sekvensnr = call.parameters["sekvensnr"]
            if (sekvensnr.isNullOrBlank() || sekvensnr.toInt() < 0) {
                call.respond(HttpStatusCode.BadRequest, "Ugyldig sekvensnr")
            } else {
                utleggstrekkService.hentUtleggstrekkFraSekvensnrOgLagreAlleNye(sekvensnr.toInt())
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}
