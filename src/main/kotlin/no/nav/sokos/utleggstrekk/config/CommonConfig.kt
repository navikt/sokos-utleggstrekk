package no.nav.sokos.utleggstrekk.config

import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.path
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.core.instrument.binder.system.UptimeMetrics
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import no.nav.sokos.utleggstrekk.metrics.Metrics
import org.slf4j.event.Level
import java.util.UUID

private val logger = KotlinLogging.logger { }

@OptIn(ExperimentalSerializationApi::class)
fun Application.commonConfig(
    azureConfiguration: AzureConfiguration,
) {
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
    install(ContentNegotiation) {
        json(
            Json {
                prettyPrint = true
                isLenient = true
                explicitNulls = false
                ignoreUnknownKeys = true
            },
        )
    }
    install(MicrometerMetrics) {
        registry = Metrics.registry
        meterBinders =
            listOf(
                UptimeMetrics(),
                JvmMemoryMetrics(),
                JvmGcMetrics(),
                JvmThreadMetrics(),
                ProcessorMetrics(),
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