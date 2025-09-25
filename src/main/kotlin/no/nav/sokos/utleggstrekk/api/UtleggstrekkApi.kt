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
        get("hentAlleFullPakke") {
            val resultat = utleggsTrekkService.hentOgSendUtleggstrekk()
            call.respond(HttpStatusCode.OK, "Antall meldinger sendt: $resultat")
        }

        get("hentnye") {
            utleggsTrekkService.hentAlleNyeUtleggstrekk()
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

private fun Route.authenticate(useAuthentication: Boolean, block: Route.() -> Unit) {
    if (useAuthentication) {
        authenticate { block() }
    } else {
        block()
    }
}
