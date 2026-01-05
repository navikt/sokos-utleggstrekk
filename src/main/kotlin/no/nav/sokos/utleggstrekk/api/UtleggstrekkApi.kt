package no.nav.sokos.utleggstrekk.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import mu.KotlinLogging

import no.nav.sokos.skattekort.security.AuthToken.getSaksbehandler
import no.nav.sokos.utleggstrekk.config.TEAM_LOGS_MARKER
import no.nav.sokos.utleggstrekk.service.UtleggsTrekkService

private val logger = KotlinLogging.logger { }

fun Routing.utleggstrekkApi(utleggsTrekkService: UtleggsTrekkService) {
    route("utleggstrekk") {
        get("hentNyeTrekk") {
            val saksbehandler = getSaksbehandler(call)

            utleggsTrekkService.schedule()
            logger.info(marker = TEAM_LOGS_MARKER) { "Manuell aktivering av henting av utleggstrekk av ${saksbehandler.ident} med roller '${saksbehandler.roller}'" }
            call.respond(HttpStatusCode.OK, "Sender trekk til OS")
        }
    }
}
