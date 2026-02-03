package no.nav.sokos.utleggstrekk.util

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json

import no.nav.sokos.utleggstrekk.config.jsonConfig

object MockHttpClient {
    private val headers = headersOf("Content-Type" to listOf(ContentType.Application.Json.toString()))

    fun getEngine(content: String = "[]"): MockEngine = getEngine(Pair(HttpStatusCode.OK, content))

    fun getEngine(vararg responses: Pair<HttpStatusCode, String>): MockEngine {
        val config =
            MockEngineConfig().apply {
                responses.forEach { (statusCode, content) ->
                    addHandler {
                        if (statusCode.isSuccess()) {
                            respond(content, statusCode, headers)
                        } else {
                            respondError(statusCode, content, headers)
                        }
                    }
                }
            }

        return MockEngine(config)
    }

    fun getClient(engine: MockEngine = getEngine()) =
        HttpClient(engine) {
            install(ContentNegotiation) { json(jsonConfig) }
            expectSuccess = false
        }
}