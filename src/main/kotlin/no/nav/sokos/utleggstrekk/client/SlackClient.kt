package no.nav.sokos.utleggstrekk.client

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.contentType
import no.nav.sokos.utleggstrekk.config.PropertiesConfig
import no.nav.sokos.utleggstrekk.slack.createSlackMessage

class SlackClient(
    private val slackEndpoint: String = PropertiesConfig.SlackConfig.url,
    private val client: HttpClient = httpClient,
) {
    suspend fun sendMessage(
        header: String,
        fileName: String,
        messages: Map<String, List<String>>,
    ) {
        client.post(
            HttpRequestBuilder().apply {
                url(slackEndpoint)
                contentType(ContentType.Application.Json)
                setBody(createSlackMessage(header, fileName, messages))
            },
        )
    }

    suspend fun sendMessage(
        header: String,
        fileName: String,
        messages: List<Pair<String, String>>,
    ) = sendMessage(header, fileName, messages.groupBy({ it.first }, { it.second }))

    suspend fun sendMessage(
        header: String,
        fileName: String,
        message: Pair<String, String>,
    ) = sendMessage(header, fileName, mapOf(message.first to listOf(message.second)))
}
