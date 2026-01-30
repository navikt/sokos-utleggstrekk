package no.nav.sokos.utleggstrekk.client

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.contentType

import no.nav.sokos.utleggstrekk.config.PropertiesConfig
import no.nav.sokos.utleggstrekk.domene.nav.createSlackMessage
import no.nav.sokos.utleggstrekk.service.ErrorMessage

class SlackClient(
    private val slackEndpoint: String = PropertiesConfig.slackConfig.url,
    private val client: HttpClient = httpClient,
) {
    suspend fun sendMessage(header: String, messages: List<ErrorMessage>) {
        client.post {
            url(slackEndpoint)
            contentType(ContentType.Application.Json)
            setBody(createSlackMessage(header, messages))
        }
    }
}