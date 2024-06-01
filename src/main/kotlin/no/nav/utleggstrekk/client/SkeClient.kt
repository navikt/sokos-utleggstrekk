package sokos.ske.krav.client

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import no.nav.utleggstrekk.client.MaskinportenAccessTokenClient
import no.nav.utleggstrekk.client.httpClient
import java.util.*

private const val NAV_ORGNR = "<SETT INN NAV ORGNR>"
private const val HENT_KRAVLISTE = "api/utleggstrekk/%s/$NAV_ORGNR"
private const val HENT_KRAV = "api/utleggstrekk/%s/$NAV_ORGNR/%s/%s"


class SkeClient(
    private val tokenProvider: MaskinportenAccessTokenClient,
    private val skeEndpoint: String,
    private val client: HttpClient = httpClient,
) {

    suspend fun getHentKravliste() =
        doGet(HENT_KRAVLISTE, UUID.randomUUID().toString())

    suspend fun getHentKrav(trekkId: String, trekkVersion: String) =
        doGet(String.format(HENT_KRAV, trekkId, trekkVersion ), UUID.randomUUID().toString())

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
