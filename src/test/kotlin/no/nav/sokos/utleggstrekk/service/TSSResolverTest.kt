@file:OptIn(ExperimentalTime::class)

package no.nav.sokos.utleggstrekk.service

import kotlin.time.ExperimentalTime

import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

import no.nav.sokos.utleggstrekk.config.PropertiesConfig
import no.nav.sokos.utleggstrekk.database.model.BetalingsinformasjonFraSkatt
import no.nav.sokos.utleggstrekk.utils.TssIdResolver

class TSSResolverTest :
    FunSpec({

        test("hvis vi spør med korrekt ornr og konto skal vi få TSS id") {
            val tssId =
                TssIdResolver.resolve(
                    mockk<BetalingsinformasjonFraSkatt>(relaxed = true) {
                        every { betalingsmottaker } returns PropertiesConfig.SKEConfig().skeOrgNr
                        every { kontonummer } returns PropertiesConfig.SKEConfig().skeKontoNr
                    },
                )
            tssId shouldBe PropertiesConfig.SKEConfig().skeTSSId
        }

        test("hvis vi spør med feil orgid skal vi få NotImplementedError exception") {
            shouldThrowExactly<IllegalArgumentException> {
                TssIdResolver.resolve(
                    mockk<BetalingsinformasjonFraSkatt>(relaxed = true) {
                        every { betalingsmottaker } returns "123456789"
                    },
                )
            }
        }

        test("hvis vi spør med feil konto skal vi få NotImplementedError exception") {
            shouldThrowExactly<IllegalArgumentException> {
                TssIdResolver.resolve(
                    mockk<BetalingsinformasjonFraSkatt>(relaxed = true) {
                        every { kontonummer } returns "123456789"
                    },
                )
            }
        }
    })