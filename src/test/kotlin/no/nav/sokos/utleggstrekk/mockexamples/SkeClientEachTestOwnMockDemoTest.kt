package no.nav.sokos.utleggstrekk.mockexamples

/*
 * Demonstrates the frozen-val problem in a self-contained two-test class.
 *
 * Because SkeClientValLoggerDemoTest already loaded SkeClient.kt before this class
 * runs, we cannot rely on "first load happens here". Instead we prove the same
 * point differently: both tests install their OWN mock before touching SkeClient,
 * but only the first one ever gets a call — the second test's mock is a completely
 * different object, yet SkeClient keeps using whichever logger was captured the
 * very first time the file-level val was evaluated (in this JVM run: test 1 below,
 * or even earlier by SkeClientValLoggerDemoTest).
 *
 * The key observation:
 *   - Test 1 installs mockLoggerA, calls SkeClient → mockLoggerA receives the info call ✅
 *   - Test 2 installs mockLoggerB, calls SkeClient → mockLoggerB receives ZERO calls ❌
 *     because the val already holds whatever logger was current at first class-load,
 *     and mockkObject cannot replace a reference that is already stored.
 *
 * If SkeClient used `private fun logger()` instead of `private val logger`, both
 * tests would pass — each call to logger() would go through KotlinLogging at
 * runtime, picking up whichever mock is active at that moment.
 */

import io.kotest.core.spec.style.FunSpec
import io.ktor.server.config.ApplicationConfig
import io.mockk.clearAllMocks
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkObject
import io.mockk.verify
import mu.KLogger
import mu.KotlinLogging

import no.nav.sokos.utleggstrekk.client.SkeClient
import no.nav.sokos.utleggstrekk.config.PropertiesConfig
import no.nav.sokos.utleggstrekk.security.maskinporten.MaskinportenAccessTokenClient
import no.nav.sokos.utleggstrekk.service.SlackService
import no.nav.sokos.utleggstrekk.util.MockHttpClient.getClient
import no.nav.sokos.utleggstrekk.util.MockHttpClient.getEngine

class SkeClientEachTestOwnMockDemoTest :
    FunSpec({

        val slackService = mockk<SlackService>(relaxUnitFun = true)
        val mockTokenProvider =
            mockk<MaskinportenAccessTokenClient> {
                coEvery { getAccessToken() } returns "mock-token"
            }

        beforeSpec {
            mockkObject(PropertiesConfig)
            every { PropertiesConfig.config } returns ApplicationConfig("application-test.conf")
        }

        afterSpec {
            unmockkObject(PropertiesConfig)
            clearAllMocks()
        }

        // ── Test 1 ────────────────────────────────────────────────────────────────────────────
        // Installs mockLoggerA and calls SkeClient.
        // Whether the class was loaded here or earlier (by SkeClientValLoggerDemoTest),
        // the val already holds whatever logger was current at first load.
        // If that happened to be while mockLoggerA was active, this passes.
        // If it happened earlier (real logger or another mock), this also fails.
        //
        // Run this class in isolation (./gradlew test --tests "...SkeClientEachTestOwnMockDemoTest")
        // to see test 1 pass and test 2 fail cleanly.
        test("test 1 – own mock (mockLoggerA): receives the info call only if class loads here first") {
            val mockLoggerA =
                mockk<KLogger>(relaxed = true) {
                    every { info(any<() -> Unit>()) } just runs
                }

            mockkObject(KotlinLogging)
            every { KotlinLogging.logger(any<() -> Unit>()) } returns mockLoggerA

            try {
                val skeClient = SkeClient(getClient(getEngine()), slackService, mockTokenProvider)

                // hentUtleggstrekkFraSekvensnr calls logger.info { "Henter utleggstrekk..." }
                skeClient.hentUtleggstrekkFraSekvensnr(1)

                // PASSES when this test is first to load SkeClient — the val is captured while
                // mockLoggerA is active, so it IS mockLoggerA.
                // FAILS when another test loaded SkeClient first — the val is already frozen.
                verify(exactly = 1) { mockLoggerA.info(any<() -> Unit>()) }
            } finally {
                unmockkObject(KotlinLogging)
                clearMocks(mockLoggerA)
            }
        }

        // ── Test 2 ────────────────────────────────────────────────────────────────────────────
        // Installs a completely different mock (mockLoggerB) and calls SkeClient again.
        // Even though this looks identical to test 1, it ALWAYS fails:
        //
        //   private val logger = KotlinLogging.logger { }
        //
        // …was evaluated exactly once — in test 1 (or even earlier). The JVM will never
        // re-run a file-level val initialiser. mockLoggerB is a brand-new object that
        // SkeClient has never heard of, and it never will.
        test("test 2 – own mock (mockLoggerB): ALWAYS fails – val frozen from test 1, new mock is ignored") {
            val mockLoggerB =
                mockk<KLogger>(relaxed = true) {
                    every { info(any<() -> Unit>()) } just runs
                }

            mockkObject(KotlinLogging)
            every { KotlinLogging.logger(any<() -> Unit>()) } returns mockLoggerB

            try {
                // A fresh SkeClient instance — but the file-level val is shared across all
                // instances. Constructing a new SkeClient does not re-evaluate `private val logger`.
                val skeClient = SkeClient(getClient(getEngine()), slackService, mockTokenProvider)

                skeClient.hentUtleggstrekkFraSekvensnr(1)

                // ALWAYS FAILS:
                //   Verification failed: KLogger(#N).info(...) was not called.
                // mockLoggerB is a different object from what the val holds.
                // SkeClient's logger.info call goes to the frozen val — not mockLoggerB.
                verify(exactly = 1) { mockLoggerB.info(any<() -> Unit>()) }
            } finally {
                unmockkObject(KotlinLogging)
                clearMocks(mockLoggerB)
            }
        }
    })
