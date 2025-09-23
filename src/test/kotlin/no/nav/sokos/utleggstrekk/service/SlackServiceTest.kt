package no.nav.sokos.utleggstrekk.service

import io.kotest.core.spec.style.FunSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk

import no.nav.sokos.utleggstrekk.client.SlackClient

class SlackServiceTest :
    FunSpec({
        val client = mockk<SlackClient>()

        test("sendError sends one slack message with one error") {
            coEvery { client.sendMessage(any(), any(), any()) } returns Unit

            val service = SlackService(client)

            service.sendError("header", "filename", ErrorMessages("type", listOf("info")))

            coVerify { client.sendMessage("header", "filename", mapOf("type" to listOf("info"))) }
        }

        test("sendError sends one slack message with several errors") {
            coEvery { client.sendMessage(any(), any(), any()) } returns Unit

            val service = SlackService(client)

            val error1 = ErrorMessages("type1", listOf("info11", "info12"))
            val error2 = ErrorMessages("type2", listOf("info21", "info22"))
            service.sendError("header", "filename", error1, error2)

            val expectedMessages =
                mapOf(
                    "type1" to listOf("info11", "info12"),
                    "type2" to listOf("info21", "info22"),
                )
            coVerify { client.sendMessage("header", "filename", expectedMessages) }
        }
    })
