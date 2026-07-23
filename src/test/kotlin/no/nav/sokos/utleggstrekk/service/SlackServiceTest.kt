package no.nav.sokos.utleggstrekk.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot

import no.nav.sokos.utleggstrekk.client.SlackClient
import no.nav.sokos.utleggstrekk.domene.nav.ErrorCategory
import no.nav.sokos.utleggstrekk.domene.nav.ErrorHeader

class SlackServiceTest :
    FunSpec({
        val client = mockk<SlackClient>()

        test("addErrorSuspending lagre en ny feil når typen er ny") {
            val service = SlackService(client)
            service.addErrorSuspending(ErrorHeader.TSSID_FEIL, "message")
            service.addErrorSuspending(ErrorHeader.FEIL_VED_SENDING, "message2", "referenceId")

            val errors = service.errorTracking()
            errors shouldHaveSize 2
            with(errors.first()) {
                type shouldBe ErrorHeader.TSSID_FEIL
                info shouldHaveSize 1
                info.forOne { (description, referenceId) ->
                    description shouldBe "message"
                    referenceId shouldBe REFERENCE_ID_DEFAULT
                }
            }
            with(errors.last()) {
                type shouldBe ErrorHeader.FEIL_VED_SENDING
                info shouldHaveSize 1
                info.forOne { (description, referenceId) ->
                    description shouldBe "message2"
                    referenceId shouldBe "referenceId"
                }
            }
        }

        test("addErrorSuspending oppdaterer feilen når typen har allerede blitt lagret") {
            val service = SlackService(client)
            service.addErrorSuspending(ErrorHeader.TSSID_FEIL, "message 1")
            service.addErrorSuspending(ErrorHeader.TSSID_FEIL, "message 2")
            service.addErrorSuspending(ErrorHeader.TSSID_FEIL, "message 3", "referenceId")

            val errors = service.errorTracking()
            errors shouldHaveSize 1
            with(errors.first()) {
                type shouldBe ErrorHeader.TSSID_FEIL
                info shouldHaveSize 3
                info.forOne { (description, referenceId) ->
                    description shouldBe "message 1"
                    referenceId shouldBe REFERENCE_ID_DEFAULT
                }
                info.forOne { (description, referenceId) ->
                    description shouldBe "message 2"
                    referenceId shouldBe REFERENCE_ID_DEFAULT
                }
                info.forOne { (description, referenceId) ->
                    description shouldBe "message 3"
                    referenceId shouldBe "referenceId"
                }
            }
        }

        test("addErrorSuspending sende alle lagret feilene som de er når de har mindre enn 5 info blocks") {
            coJustRun { client.sendMessage(any(), any()) }

            val service = SlackService(client)
            service.addErrorSuspending(ErrorHeader.FEIL_FRA_SKE, "Info 1")
            service.addErrorSuspending(ErrorHeader.FEIL_FRA_SKE, "Info 2", "referenceId12")
            service.addErrorSuspending(ErrorHeader.FEIL_VED_SENDING, "Info 1", "referenceId21")
            service.addErrorSuspending(ErrorHeader.FEIL_VED_SENDING, "Info 2")

            service.sendCachedErrors(ErrorCategory.TREKK_HENTING)

            val messages = slot<List<ErrorMessage>>()
            coVerify(exactly = 1) { client.sendMessage(ErrorCategory.TREKK_HENTING.value, capture(messages)) }

            messages.captured shouldHaveSize 2
            messages.captured.forOne { (type, info) ->
                type shouldBe ErrorHeader.FEIL_FRA_SKE
                info.shouldContainExactlyInAnyOrder(
                    ErrorInfo("Info 1"),
                    ErrorInfo("Info 2", "referenceId12"),
                )
            }
            messages.captured.forOne { (type, info) ->
                type shouldBe ErrorHeader.FEIL_VED_SENDING
                info.shouldContainExactlyInAnyOrder(
                    ErrorInfo("Info 1", "referenceId21"),
                    ErrorInfo("Info 2"),
                )
            }

            service.errorTracking().shouldBeEmpty()
        }

        test("sendError konsolidere feilene når de har mer enn 5 info blocks før sending meldingen") {
            coJustRun { client.sendMessage(any(), any()) }

            val service = SlackService(client)
            repeat(5) {
                service.addErrorSuspending(ErrorHeader.FEIL_FRA_SKE, "Info ${it + 1}")
            }

            service.addErrorSuspending(ErrorHeader.FEIL_VED_SENDING, "Info 1")
            repeat(6) {
                service.addErrorSuspending(ErrorHeader.FEIL_VED_SENDING, "Info ${it + 2}", "referenceId${it + 2}")
            }

            service.sendCachedErrors(ErrorCategory.TREKK_HENTING)

            val messages = slot<List<ErrorMessage>>()
            coVerify(exactly = 1) { client.sendMessage(ErrorCategory.TREKK_HENTING.value, capture(messages)) }

            messages.captured shouldHaveSize 2
            messages.captured.forOne { (type, info) ->
                type shouldBe ErrorHeader.FEIL_FRA_SKE
                info shouldBe MutableList(5) { ErrorInfo("Info ${it + 1}", REFERENCE_ID_DEFAULT) }
            }
            messages.captured.forOne { (type, info) ->
                type shouldBe ErrorHeader.FEIL_VED_SENDING

                val expectedErrorInfo =
                    ErrorInfo(
                        "7 av samme type feil. Sjekk avstemming",
                        "referenceId2, referenceId3, referenceId4, referenceId5, referenceId6, referenceId7",
                    )
                info shouldBe mutableListOf(expectedErrorInfo)
            }
        }

        test("sendError sender igen melding når det er ingen lagret feil") {
            coEvery { client.sendMessage(any(), any()) } returns Unit

            val service = SlackService(client)
            service.sendCachedErrors(ErrorCategory.TSS_ID)

            coVerify(exactly = 0) { client.sendMessage(any(), any()) }
        }

        test("sendCachedErrors re-queues meldinger og kaster exception når sendMessage feiler") {
            val expectedException = RuntimeException("Slack er nede")
            coEvery { client.sendMessage(any(), any()) } throws expectedException

            val service = SlackService(client)
            service.addErrorSuspending(ErrorHeader.FEIL_FRA_SKE, "Info 1")
            service.addErrorSuspending(ErrorHeader.FEIL_FRA_SKE, "Info 2", "referenceId2")
            service.addErrorSuspending(ErrorHeader.FEIL_VED_SENDING, "Info 3")

            val thrownException =
                runCatching { service.sendCachedErrors(ErrorCategory.TREKK_HENTING) }
                    .exceptionOrNull()

            thrownException shouldBe expectedException

            val requeued = service.errorTracking()
            requeued.size shouldBe 2
            requeued.forOne { (type, info) ->
                type shouldBe ErrorHeader.FEIL_FRA_SKE
                info.shouldContainExactlyInAnyOrder(
                    ErrorInfo("Info 1", REFERENCE_ID_DEFAULT),
                    ErrorInfo("Info 2", "referenceId2"),
                )
            }
            requeued.forOne { (type, info) ->
                type shouldBe ErrorHeader.FEIL_VED_SENDING
                info.shouldContainExactly(ErrorInfo("Info 3", REFERENCE_ID_DEFAULT))
            }
        }

        afterTest {
            clearMocks(client)
        }
    })
