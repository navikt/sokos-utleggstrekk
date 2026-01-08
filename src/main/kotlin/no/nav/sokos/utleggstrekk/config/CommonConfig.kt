package no.nav.sokos.utleggstrekk.config

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.path
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.core.instrument.binder.system.UptimeMetrics
import mu.KotlinLogging
import org.slf4j.Marker
import org.slf4j.MarkerFactory
import org.slf4j.event.Level

import no.nav.sokos.utleggstrekk.metrics.Metrics

private val logger = KotlinLogging.logger { }

// Add this marker to the logger when sending logs with secrets
val TEAM_LOGS_MARKER: Marker = MarkerFactory.getMarker("TEAM_LOGS")

val jsonConfig =
    Json {
        explicitNulls = false
        encodeDefaults = true
        prettyPrint = true
    }

@OptIn(ExperimentalSerializationApi::class)
fun Application.commonConfig() {
    install(CallLogging) {
        logger = no.nav.sokos.utleggstrekk.config.logger
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/api") }
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
}