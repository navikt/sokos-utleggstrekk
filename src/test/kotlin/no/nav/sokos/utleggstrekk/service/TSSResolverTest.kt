@file:OptIn(ExperimentalTime::class)

package no.nav.sokos.utleggstrekk.service

import kotlin.time.ExperimentalTime

import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.server.config.ApplicationConfig
import io.mockk.clearAllMocks
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify

import no.nav.sokos.utleggstrekk.config.PropertiesConfig
import no.nav.sokos.utleggstrekk.config.PropertiesConfig.skeConfig
import no.nav.sokos.utleggstrekk.database.model.BetalingsinformasjonFraSkatt
import no.nav.sokos.utleggstrekk.domene.nav.ErrorHeader
import no.nav.sokos.utleggstrekk.utils.TssIdResolver

class TSSResolverTest :
    FunSpec({
        val slackService = mockk<SlackService>(relaxUnitFun = true)

        beforeSpec {
            mockkObject(SlackService.Companion, PropertiesConfig)
            every { SlackService.instance } returns slackService
            every { PropertiesConfig.config } returns ApplicationConfig("application-test.conf")
        }

        test("hvis vi spør med korrekt ornr og konto skal vi få TSS id") {
            val tssId =
                TssIdResolver.resolve(
                    mockk<BetalingsinformasjonFraSkatt>(relaxed = true) {
                        every { betalingsmottaker } returns skeConfig.skeOrgNr
                        every { kontonummer } returns skeConfig.skeKontoNr
                    },
                )
            tssId shouldBe skeConfig.skeTSSId
        }

        test("hvis vi spør med feil orgid skal vi få NotImplementedError exception og sende en varsel") {
            shouldThrowExactly<IllegalArgumentException> {
                TssIdResolver.resolve(
                    mockk<BetalingsinformasjonFraSkatt>(relaxed = true) {
                        every { betalingsmottaker } returns "123456789"
                    },
                )

                val message = slot<String>()
                verify(exactly = 1) { slackService.addError(ErrorHeader.TSSID_FEIL, capture(message)) }
                coVerify(exactly = 1) { slackService.sendCachedErrors("TssIdResolver failed") }
                message.captured.shouldContain("Kombinasjonen Orgnr=123456789")
            }
        }

        test("hvis vi spør med feil konto skal vi få NotImplementedError exception og sende en varsel") {
            shouldThrowExactly<IllegalArgumentException> {
                TssIdResolver.resolve(
                    mockk<BetalingsinformasjonFraSkatt>(relaxed = true) {
                        every { kontonummer } returns "123456789"
                    },
                )

                val message = slot<String>()
                verify(exactly = 1) { slackService.addError(ErrorHeader.TSSID_FEIL, capture(message)) }
                coVerify(exactly = 1) { slackService.sendCachedErrors("TssIdResolver failed") }
                message.captured.shouldContain("Konto=123456789")
            }
        }

        afterSpec {
            clearAllMocks()
            unmockkObject(SlackService.Companion, PropertiesConfig)
        }
    })
