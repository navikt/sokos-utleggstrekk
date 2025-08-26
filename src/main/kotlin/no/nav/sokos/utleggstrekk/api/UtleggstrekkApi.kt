package no.nav.sokos.utleggstrekk.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import no.nav.sokos.utleggstrekk.service.UtleggsTrekkService

fun Routing.utleggstrekkApi(
    utleggsTrekkService: UtleggsTrekkService = UtleggsTrekkService()
) {
    route("utleggstrekk") {
        get("hentAlleFullPakke") {
            val resultat = utleggsTrekkService.hentOgSendUtleggstrekk()
            call.respond(HttpStatusCode.OK, "Antall meldinger sendt: $resultat")
        }

        get("hentnye") {
            utleggsTrekkService.hentOgLagreNyeUtleggstrekk()
            call.respond(HttpStatusCode.OK)
        }

        get("hent/{sekvensnr}") {
            val sekvensnr = call.parameters["sekvensnr"]
            if (sekvensnr.isNullOrBlank() || sekvensnr.toInt() < 0) {
                call.respond(HttpStatusCode.BadRequest, "Ugyldig sekvensnr")
            } else {
                utleggsTrekkService.hentUtleggstrekkFraSekvensnrOgLagreAlleNye(sekvensnr.toInt())
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}
