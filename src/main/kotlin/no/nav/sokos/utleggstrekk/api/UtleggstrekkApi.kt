package no.nav.sokos.utleggstrekk.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import mu.KotlinLogging
import no.nav.sokos.utleggstrekk.service.KvitteringService
import no.nav.sokos.utleggstrekk.service.UtleggstrekkService

fun Routing.utleggstrekkApi(
    utleggstrekkService: UtleggstrekkService,
    kvitteringService: KvitteringService
) {
    val logger = KotlinLogging.logger { }

    route("utleggstrekk") {
        get("hentalle") {
//            call.respond("Behandler")
            val resultat = utleggstrekkService.HentOgSendUtleggstrekk()
            call.respond(HttpStatusCode.OK, "Antall meldinger sendt: $resultat")
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
        get("hentnye") {
            val nye = utleggstrekkService.hentAlleUtleggstrekk()
            call.respond(HttpStatusCode.OK, nye)
        }
        get("kvittering") {
            val nye = kvitteringService.hentAlleKvitteringer()
            call.respond(HttpStatusCode.OK, nye.toString())
        }
        get("hentnyeOgLagre}") {
            val utleggstrekk = utleggstrekkService.hentAlleNyeUtleggstrekk()
            println("Antall Nye: ${utleggstrekk.size}")
            call.respond(HttpStatusCode.OK, utleggstrekk.toString())
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