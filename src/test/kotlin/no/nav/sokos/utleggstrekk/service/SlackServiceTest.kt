package no.nav.sokos.utleggstrekk.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot

import no.nav.sokos.utleggstrekk.client.SlackClient

class SlackServiceTest :
    FunSpec({
        val client = mockk<SlackClient>()

        test("addError lagre en ny feil når typen er ny") {
            val service = SlackService(client)
            service.addError("header", "message")

            service.errorTracking.first() shouldBe ErrorMessage("header", mutableListOf("message"))
        }

        test("addError oppdaterer feilen når typen har allerede blitt lagret") {
            val service = SlackService(client)
            service.addError("header", "message 1")
            service.addError("header", "message 2")

            service.errorTracking.size shouldBe 1
            service.errorTracking.first() shouldBe ErrorMessage("header", mutableListOf("message 1", "message 2"))
        }

        test("sendError sende alle lagret feilene som de er når de har mindre enn 5 info blocks") {
            val messages = slot<List<ErrorMessage>>()
            coEvery { client.sendMessage(any(), capture(messages)) } returns Unit

            val service = SlackService(client)
            repeat(2) { typeIndex ->
                repeat(2) { infoIndex ->
                    service.addError("Type ${typeIndex + 1}", "Info ${infoIndex + 1}")
                }
            }

            service.sendErrors("Slack Message Header")

            coVerify(exactly = 1) { client.sendMessage("Slack Message Header", any()) }

            val capturedMessages = messages.captured
            capturedMessages.size shouldBe 2
            capturedMessages.first() shouldBe ErrorMessage("Type 1", mutableListOf("Info 1", "Info 2"))
            capturedMessages.last() shouldBe ErrorMessage("Type 2", mutableListOf("Info 1", "Info 2"))

            service.errorTracking.size shouldBe 0
        }

        test("sendError konsolidere feilene når de har mer enn 5 info blocks før sending meldingen") {
            val messages = slot<List<ErrorMessage>>()
            coEvery { client.sendMessage(any(), capture(messages)) } returns Unit

            val service = SlackService(client)
            repeat(5) {
                service.addError("Type 1", "Info ${it + 1}")
            }

            repeat(6) {
                service.addError("Type 2", "Info ${it + 2}")
            }

            service.sendErrors("Slack Message Header")

            coVerify(exactly = 1) { client.sendMessage("Slack Message Header", any()) }

            val capturedMessages = messages.captured
            capturedMessages.size shouldBe 2

            capturedMessages.first().info shouldBe MutableList(5) { "Info ${it + 1}" }
            capturedMessages.last().info shouldBe mutableListOf("6 av samme type feil: Type 2. Sjekk avstemming")
        }

        afterTest {
            clearMocks(client)
        }
    })