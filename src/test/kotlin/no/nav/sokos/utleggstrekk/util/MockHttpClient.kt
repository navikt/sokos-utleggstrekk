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
    val utleggsTrekkListeUtenMidlertidigStans =
        """
        [
          {
            "trekkid": "1",
            "trekkversjon": 1,
            "sekvensnummer": 1,
            "opprettet": "2024-07-10T08:24:34.143Z",
            "trekkpliktig": "123456789",
            "skyldner": "22003648649",
            "trekkstatus": "aktiv",
            "startPeriode": "2024-12",
            "sluttPeriode": "2024-12",
            "midlertidigStans":  null,
            "trekkbeloep": {
              "trekkbeloep": 1000
            },
            "kidnummer": "8981238184016280475641088",
            "kontonummer": "19019019019"
          },
          {
            "trekkid": "2",
            "trekkversjon": 1,
            "sekvensnummer": 1,
            "opprettet": "2024-07-10T08:24:34.143Z",
            "trekkpliktig": "987654321",
            "skyldner": "22003648649",
            "trekkstatus": "inaktiv",
            "startPeriode": "2024-11",
            "sluttPeriode": "2024-11",
            "midlertidigStans": null,
            "trekkprosent": {
              "trekkprosent": 10.0
            },
            "kidnummer": "9981238184016280475641088",
            "kontonummer": "12012012012"
          }
        ]
        """.trimIndent()

    //language=json
    val utleggsTrekkListeMedMidlertidigStans =
        """
        [
          {
            "trekkid": "1",
            "trekkversjon": 1,
            "sekvensnummer": 1,
            "opprettet": "2024-07-10T08:24:34.143Z",
            "trekkpliktig": "123456789",
            "skyldner": "22003648649",
            "trekkstatus": "aktiv",
            "startPeriode": "2024-12",
            "sluttPeriode": "2024-12",
            "midlertidigStans":  [
            {
              "startPeriode": "2024-12",
              "sluttPeriode": "2024-12"
            }
          ],
            "trekkbeloep": {
              "trekkbeloep": 1000
            },
            "kidnummer": "8981238184016280475641088",
            "kontonummer": "19019019019"
          },
          {
            "trekkid": "2",
            "trekkversjon": 1,
            "sekvensnummer": 1,
            "opprettet": "2024-07-10T08:24:34.143Z",
            "trekkpliktig": "987654321",
            "skyldner": "22003648649",
            "trekkstatus": "inaktiv",
            "startPeriode": "2024-11",
            "sluttPeriode": "2024-11",
            "midlertidigStans": null,
            "trekkprosent": {
              "trekkprosent": 10.0
            },
            "kidnummer": "9981238184016280475641088",
            "kontonummer": "12012012012"
          }
        ]
        """.trimIndent()

    //language=json
    val utleggsTrekk =
        """
        {
          "trekkid": "string",
          "trekkversjon": 1,
          "sekvensnummer": 1,
          "opprettet": "2024-07-10T08:25:37.020Z",
          "trekkpliktig": "123456789",
          "skyldner": "13088839702",
          "trekkstatus": "aktiv",
          "startPeriode": "2024-12",
          "sluttPeriode": "2024-12",
          "midlertidigStans": [
            {
              "startPeriode": "2024-12",
              "sluttPeriode": "2024-12"
            }
          ],
          "trekkbeloep": {
            "trekkbeloep": 0
          },
          "trekkprosent": {
            "trekkprosent": 10.0
          },
          "kidnummer": "824776336890844418867",
          "kontonummer": "61588822927"
        }
        """.trimIndent()
}