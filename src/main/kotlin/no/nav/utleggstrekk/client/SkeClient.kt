package sokos.utleggstrekk.client

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import java.util.*

private const val NAV_ORGNR = "<SETT INN NAV ORGNR>"
private const val HENT_NYE_UTLEGGSTREKK = "api/utleggstrekk/%s/$NAV_ORGNR"
private const val HENT_ENKELT_TREKK = "api/utleggstrekk/%s/$NAV_ORGNR/%s/%s"


class SkeClient(
    private val tokenProvider: MaskinportenAccessTokenClient,
    private val skeEndpoint: String,
    private val client: HttpClient = httpClient,
) {


    private suspend fun doGet(path: String, corrID: String) = client.get(buildHttpRequest(path, corrID))

    private suspend fun buildHttpRequest(path: String, corrID: String): HttpRequestBuilder {
        val token = tokenProvider.hentAccessToken()
        return HttpRequestBuilder().apply {
            url("$skeEndpoint$path")
            headers {
                append("Korrelasjonsid", corrID)
                append(HttpHeaders.Authorization, "Bearer $token")
            }
        }
    }
}
