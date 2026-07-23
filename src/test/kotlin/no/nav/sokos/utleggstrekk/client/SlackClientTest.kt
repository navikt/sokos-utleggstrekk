package no.nav.sokos.utleggstrekk.client

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify

import no.nav.sokos.utleggstrekk.domene.nav.Data
import no.nav.sokos.utleggstrekk.domene.nav.ErrorCategory
import no.nav.sokos.utleggstrekk.domene.nav.ErrorHeader
import no.nav.sokos.utleggstrekk.domene.nav.createSlackMessage
import no.nav.sokos.utleggstrekk.service.ErrorMessage
import no.nav.sokos.utleggstrekk.util.MockHttpClient

class SlackClientTest :
    FunSpec({
        val endpoint = "slack_endpoint"

        test("sendMessage skal POST en melding til slack") {
            mockkStatic(::createSlackMessage)
            every { createSlackMessage(any(), any()) } returns Data("", emptyList())

            val engine = MockHttpClient.getEngine("")
            val slackClient = SlackClient(endpoint, MockHttpClient.getClient(engine))

            val messages =
                listOf(
                    ErrorMessage(ErrorHeader.FEIL_VED_SENDING, "Info 1"),
                    ErrorMessage(ErrorHeader.FEIL_FRA_SKE, "Info 2"),
                )
            slackClient.sendMessage(ErrorCategory.TREKK_HENTING, messages)

            engine.requestHistory shouldHaveSize 1
            val request = engine.requestHistory.first()
            request.url.toString() shouldContain endpoint
            request.method shouldBe HttpMethod.Post

            val body = request.body
            body.contentType shouldBe ContentType.Application.Json

            verify(exactly = 1) { createSlackMessage(ErrorCategory.TREKK_HENTING, messages) }

            unmockkStatic(::createSlackMessage)
        }
    })
