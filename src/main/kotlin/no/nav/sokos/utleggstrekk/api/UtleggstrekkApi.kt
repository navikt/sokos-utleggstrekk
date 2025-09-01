package no.nav.sokos.utleggstrekk.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.route

import no.nav.sokos.utleggstrekk.service.KvitteringService
import no.nav.sokos.utleggstrekk.service.UtleggsTrekkService

fun Routing.utleggstrekkApi(
    utleggsTrekkService: UtleggsTrekkService,
    kvitteringService: KvitteringService,
) {
    route("utleggstrekk") {
        get("hentAlleFullPakke") {
            val resultat = utleggsTrekkService.hentOgSendUtleggstrekk()
            call.respond(HttpStatusCode.OK, "Antall meldinger sendt: $resultat")
        }

        get("bareHentkvittering") {
            println("Henter kvitteringer")
            val nye = kvitteringService.hentAlleKvitteringer()
            println("Kvitteringer mottatt: ${nye.size}")
            call.respond(HttpStatusCode.OK, nye.toString())
        }

        get("behandlekvittering") {
            println("Behandler kvitteringer")
            kvitteringService.behandleKvitteringer()
            call.respond(HttpStatusCode.OK)
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
