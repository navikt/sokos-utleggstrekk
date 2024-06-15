package sokos.utleggstrekk.config

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.path
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.core.instrument.binder.system.UptimeMetrics
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.slf4j.event.Level
import sokos.utleggstrekk.HttpServer
import sokos.utleggstrekk.metrics.Metrics
import java.util.*

@OptIn(ExperimentalSerializationApi::class)
fun Application.commonConfig() {

    install(CallId) {
        header("nav-call-id")
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
        json(Json {
            prettyPrint = true
            isLenient = true
            explicitNulls = false
        })
    }

    install(MicrometerMetrics) {
        registry = Metrics.registry
        meterBinders = listOf(
            UptimeMetrics(),
            JvmMemoryMetrics(),
            JvmGcMetrics(),
            JvmThreadMetrics(),
            ProcessorMetrics(),
        )
    }

}

fun Routing.internalNaisRoutes(
    applicationState: HttpServer.ApplicationState,
    readynessCheck: () -> Boolean = { applicationState.ready },
    alivenessCheck: () -> Boolean = { applicationState.alive },
) {
    route("internal") {
        get("isAlive") {
            when (alivenessCheck()) {
                true -> call.respondText { "Utleggstrekk is alive" }
                else ->
                    call.respondText(
                        text = "Utleggstrekk is DEAD!",
                        status = HttpStatusCode.InternalServerError,
                    )
            }
        }
        get("isReady") {
            when (readynessCheck()) {
                true -> call.respondText { "Utleggstrekk is ready!" }
                else ->
                    call.respondText(
                        text = "Wait! Utlegsstrekk is not ready.... yet! ",
                        status = HttpStatusCode.InternalServerError,
                    )
            }
        }
        get("metrics") {
            call.respondText(Metrics.registry.scrape())
        }
    }
}