package no.nav.sokos.utleggstrekk.config

import java.util.UUID

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.path
import mu.KotlinLogging
import org.slf4j.event.Level

private val logger = KotlinLogging.logger { }

val jsonConfig =
    Json {
        explicitNulls = false
        encodeDefaults = true
        prettyPrint = true
    }

// TODO: Bytte navn. Dette er ikke "common".
@OptIn(ExperimentalSerializationApi::class) // TODO: Sjekk om denne kan fjernes
fun Application.commonConfig(azureConfiguration: AzureConfiguration) {
    install(CallId) {
        header(HttpHeaders.XCorrelationId)
        generate { UUID.randomUUID().toString() }
        verify { it.isNotEmpty() }
    }
    install(CallLogging) {
        level = Level.INFO
        callIdMdc(HttpHeaders.XCorrelationId)
        filter { call -> call.request.path().startsWith("/utleggstrekk") }
        disableDefaultColors()
    }
    // TODO: flytt til et object
    install(ContentNegotiation) {
        json(
            Json {
                prettyPrint = true
                isLenient = true
                decodeEnumsCaseInsensitive = true
                explicitNulls = true
                encodeDefaults = true
            },
        )
    }

    install(Authentication) {
        jwt {
            verifier(azureConfiguration.azureAd.jwkProvider, azureConfiguration.azureAd.openIdConfiguration.issuer)
            realm = azureConfiguration.appName
            validate { cred ->
                try {
                    requireNotNull(cred.payload.audience) { "Ikke gyldig Token, mangler audience" }
                    require(cred.payload.audience.contains(azureConfiguration.azureAd.clientId)) { "Ikke gyldig Token, ikke gyldig audienceclaim" }
                    JWTPrincipal(cred.payload)
                } catch (e: Exception) {
                    logger.warn(e.message)
                    null
                }
            }
        }
    }
}