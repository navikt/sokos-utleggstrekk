package no.nav.sokos.utleggstrekk.client

import kotlinx.serialization.json.Json

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify

import no.nav.sokos.utleggstrekk.domene.nav.Data
import no.nav.sokos.utleggstrekk.domene.nav.createSlackMessage
import no.nav.sokos.utleggstrekk.service.ErrorMessage

class SlackClientTest :
    FunSpec({
        val endpoint = "slack_endpoint"

        test("sendMessage skal POST en melding til slack") {
            mockkStatic(::createSlackMessage)
            every { createSlackMessage(any(), any()) } returns Data("", emptyList())

            val engine =
                MockEngine {
                    respond("")
                }
            val slackClient = SlackClient(endpoint, mockClient(engine))

            val header = "Message header"
            val messages =
                listOf(
                    ErrorMessage("Feil 1", mutableListOf("Info 1")),
                    ErrorMessage("Feil 2", mutableListOf("Info 2")),
                )
            slackClient.sendMessage(header, messages)

            engine.requestHistory shouldHaveSize 1
            val request = engine.requestHistory.first()
            request.url.toString() shouldContain endpoint
            request.method shouldBe HttpMethod.Post

            val body = request.body
            body.contentType shouldBe ContentType.Application.Json

            verify(exactly = 1) { createSlackMessage(header, messages) }

            unmockkStatic(::createSlackMessage)
        }
    })

private fun mockClient(engine: MockEngine) =
    HttpClient(engine) {
        install(ContentNegotiation) {
            json(
                Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                },
            )
        }
        expectSuccess = false
    }