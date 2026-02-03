package no.nav.sokos.utleggstrekk.client

import java.net.ProxySelector

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import org.apache.hc.client5.http.impl.routing.SystemDefaultRoutePlanner

import no.nav.sokos.utleggstrekk.config.jsonConfig

val httpClient =
    HttpClient(Apache5) {
        expectSuccess = false

        install(HttpRequestRetry) {
            retryOnException(maxRetries = 3)
            delayMillis { retry -> retry * 3000L }
        }

        install(ContentNegotiation) { json(jsonConfig) }

        engine {
            customizeClient {
                setRoutePlanner(SystemDefaultRoutePlanner(ProxySelector.getDefault()))
            }
        }
    }