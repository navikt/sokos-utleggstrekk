package no.nav.sokos.utleggstrekk.client

import java.net.ProxySelector

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import org.apache.http.impl.conn.SystemDefaultRoutePlanner

// TODO: Sett json config her
val httpClient =
    HttpClient(Apache) {
        expectSuccess = false

        install(HttpRequestRetry) {
            retryOnException(maxRetries = 3)
            delayMillis { retry -> retry * 3000L }
        }

        @OptIn(ExperimentalSerializationApi::class)
        install(ContentNegotiation) {
            json(
                Json {
                    prettyPrint = true
                    isLenient = true
                    explicitNulls = false
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                },
            )
        }

        engine {
            customizeClient {
                setRoutePlanner(SystemDefaultRoutePlanner(ProxySelector.getDefault()))
            }
        }
    }