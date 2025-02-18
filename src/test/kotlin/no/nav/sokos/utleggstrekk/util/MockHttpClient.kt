package no.nav.sokos.utleggstrekk.util

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

class MockHttpClient {
    private val orgNr = "889640782"
    private val responseHeaders = headersOf("Content-Type" to listOf(ContentType.Application.Json.toString()))
    private val jsonConfig =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
            @OptIn(ExperimentalSerializationApi::class)
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

object Responses {
    //language=json
    val utleggsTrekkListeFraSkatt =
        """
[
  {
    "trekkid": "1",
    "trekkversjon": 1,
    "sekvensnummer": 1,
    "opprettet": "2024-06-16T13:33:05.672Z",
    "saksnummer": "sak-2023-899",
    "trekkpliktig": "889640782",
    "skyldner": "19628198007",
    "trekkstatus": "aktiv",
    "trekkstoerrelseForPeriode": [
      {
        "startdato": "2023-06-13",
        "sluttdato": "2024-11-30",
        "trekkbeloep": {
          "trekkbeloep": 5000.0
        }
      },
      {
        "startdato": "2024-12-01",
        "sluttdato": "2024-12-31",
        "trekkbeloep": {
          "trekkbeloep": 0.0
        }
      },
      {
        "startdato": "2025-01-01",
        "trekkbeloep": {
          "trekkbeloep": 5000.0
        }
      }
    ],
    "betalingsinformasjon": {
      "betalingsmottaker": "971648198",
      "kidnummer": "17654202404",
      "kontonummer": "76940512057"
    }
  },
  {
    "trekkid": "2_xx",
    "trekkversjon": 1,
    "sekvensnummer": 2,
    "opprettet": "2024-06-16T14:33:05.672Z",
    "saksnummer": "sak-2023-900",
    "trekkpliktig": "889640782",
    "skyldner": "11656296129",
    "trekkstatus": "aktiv",
    "trekkstoerrelseForPeriode": [
      {
        "startdato": "2023-06-13",
        "sluttdato": "2024-11-30",
        "trekkbeloep": {
          "trekkbeloep": 800.5
        }
      }
    ],
    "betalingsinformasjon": {
      "betalingsmottaker": "971648198",
      "kidnummer": "45645202404",
      "kontonummer": "76940512057"
    }
  }
] """.trimIndent()
}