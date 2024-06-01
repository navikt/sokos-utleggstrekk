package no.nav.utleggstrekk.client

import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.apache.http.impl.conn.SystemDefaultRoutePlanner
import java.net.ProxySelector

@OptIn(ExperimentalSerializationApi::class)
val httpClient = HttpClient(Apache) {
    expectSuccess = false

    install(HttpRequestRetry) {
        retryOnException(maxRetries = 3)
        delayMillis { retry -> retry * 3000L }
    }

    install(ContentNegotiation) {
        json(
            Json {
                prettyPrint = true
                ignoreUnknownKeys = true
                encodeDefaults = true
                explicitNulls = false
            },
        )
    }

    engine {
        customizeClient {
            setRoutePlanner(SystemDefaultRoutePlanner(ProxySelector.getDefault()))
        }
    }
}
