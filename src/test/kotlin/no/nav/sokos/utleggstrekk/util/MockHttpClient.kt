package no.nav.sokos.utleggstrekk.util

import kotlinx.serialization.json.Json

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json

class MockHttpClient {
    private val orgNr = "889640782"
    private val responseHeaders = headersOf("Content-Type" to listOf(ContentType.Application.Json.toString()))
    private val jsonConfig =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }
    private val baseUrl: String = "/api/utleggstrekk/v1/$orgNr"

    fun getClient(
        response: String,
        statusCode: HttpStatusCode,
    ) = HttpClient(MockEngine) {
        install(ContentNegotiation) { json(jsonConfig) }
        engine {
            addHandler { request ->
                when (request.url.encodedPath) {
                    baseUrl -> respond(response, statusCode, responseHeaders)
                    else -> {
                        error("Ikke implementert: ${request.url.encodedPath}")
                    }
                }
            }
        }
    }
}
