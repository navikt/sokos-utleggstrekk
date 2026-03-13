package no.nav.sokos.utleggstrekk.mockexamples

/*
 * Demonstrates that `private val logger` in SkeClient.kt breaks logger mocking
 * when the class is loaded before mockkObject(KotlinLogging) is called.
 *
 * Test 1 creates a SkeClient and calls it WITHOUT mocking the logger.
 * This loads SkeClient.kt's class file and freezes:
 *
 *     private val logger = KotlinLogging.logger { }   ← captured here, with the REAL logger
 *
 * Test 2 then tries to mock the logger with mockkObject(KotlinLogging) and assert
 * that SkeClient logged via the mock. This FAILS — because the val in SkeClient was
 * already set to the real logger by test 1, and mockkObject cannot retroactively
 * replace an already-captured object reference.
 *
 * The fix is `private fun logger(): KLogger = KotlinLogging.logger { }` — evaluated
 * at call-time, after mockkObject is active, always returning the mock.
 */

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
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

class SkeClientValLoggerDemoTest :
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

        // ── Test 1: use SkeClient without caring about logging ────────────────────────────────
        // This is the innocent test. It just calls SkeClient normally.
        // Side effect: SkeClient.kt's class is loaded, and `private val logger` is frozen
        // to whatever KotlinLogging.logger {} returns right now — the REAL logger,
        // because no mockkObject(KotlinLogging) is active yet.
        test("test 1 – uses SkeClient without mocking the logger (loads the class, freezes val logger)") {
            val skeClient = SkeClient(getClient(getEngine()), slackService, mockTokenProvider)

            // hentUtleggstrekkFraSekvensnr calls logger.info { "Henter utleggstrekk..." }
            // That call goes to the REAL logger — which is now frozen in SkeClient's val.
            val result = skeClient.hentUtleggstrekkFraSekvensnr(1)
            result.shouldBeEmpty()

            // No logger assertions here — this test doesn't care about logging.
            // But it has permanently frozen SkeClient's val logger to the real logger.
        }

        // ── Test 2: tries to mock the logger AFTER the class was loaded in test 1 ─────────────
        // This test FAILS — demonstrating the broken behaviour of `private val logger`.
        // The val was captured in test 1 before mockkObject was installed here.
        // mockLogger will never receive any calls from SkeClient.
        test("test 2 – mocks logger AFTER class load: verify fails because val is already frozen") {
            val mockLogger =
                mockk<KLogger>(relaxed = true) {
                    every { info(any<() -> Unit>()) } just runs
                }

            mockkObject(KotlinLogging)
            every { KotlinLogging.logger(any<() -> Unit>()) } returns mockLogger

            try {
                val skeClient = SkeClient(getClient(getEngine()), slackService, mockTokenProvider)

                // This call triggers logger.info { "Henter utleggstrekk fra sekvensnummer..." }
                // inside SkeClient. But the logger used is the val frozen in test 1 —
                // NOT mockLogger. The mock receives zero calls.
                skeClient.hentUtleggstrekkFraSekvensnr(1)

                // This verify FAILS:
                //   Verification failed: call 1 of 1: KLogger(#N).info(...) was not called.
                // Because SkeClient's frozen val bypasses mockLogger entirely.
                //
                // If SkeClient used `private fun logger()` instead of `private val logger`,
                // the call to KotlinLogging.logger {} would happen here, after mockkObject,
                // and this verify would pass.
                verify(exactly = 1) { mockLogger.info(any<() -> Unit>()) }
            } finally {
                unmockkObject(KotlinLogging)
                clearMocks(mockLogger)
            }
        }

        // ── Each-test-creates-own-mock pattern ────────────────────────────────────────────────
        // These two tests each create their OWN mockLogger and install mockkObject independently.
        // They look correct in isolation — but test B still fails, because SkeClient's val was
        // frozen in "test 1" above (or whichever test first loaded the class).
        // No matter how many times you create a new mock and call mockkObject, the val in
        // SkeClient cannot be replaced retroactively.

        test("each-test mock A – own mock, own SkeClient: PASSES only if class not yet loaded") {
            // If this test ran FIRST (before test 1 above), it would pass — the class would be
            // loaded while mockkObject is active. But since test 1 ran first, the val is already
            // the real logger, and this mock is never used by SkeClient.
            val mockLoggerA =
                mockk<KLogger>(relaxed = true) {
                    every { info(any<() -> Unit>()) } just runs
                }

            mockkObject(KotlinLogging)
            every { KotlinLogging.logger(any<() -> Unit>()) } returns mockLoggerA

            try {
                val skeClient = SkeClient(getClient(getEngine()), slackService, mockTokenProvider)
                skeClient.hentUtleggstrekkFraSekvensnr(1)

                // FAILS — val frozen in test 1, mockLoggerA never called by SkeClient.
                verify(exactly = 1) { mockLoggerA.info(any<() -> Unit>()) }
            } finally {
                unmockkObject(KotlinLogging)
                clearMocks(mockLoggerA)
            }
        }

        test("each-test mock B – own mock, own SkeClient: ALSO fails – same frozen val, different mock") {
            // A completely fresh mock — different object than mockLoggerA — but it makes no
            // difference. SkeClient's val still points to the logger captured in test 1.
            // Creating a new SkeClient instance does NOT re-evaluate the file-level val.
            val mockLoggerB =
                mockk<KLogger>(relaxed = true) {
                    every { info(any<() -> Unit>()) } just runs
                }

            mockkObject(KotlinLogging)
            every { KotlinLogging.logger(any<() -> Unit>()) } returns mockLoggerB

            try {
                // Fresh SkeClient instance — but `private val logger` is file-level, shared
                // across ALL instances of SkeClient. Creating a new instance does not re-run
                // the top-level val initialiser.
                val skeClient = SkeClient(getClient(getEngine()), slackService, mockTokenProvider)
                skeClient.hentUtleggstrekkFraSekvensnr(1)

                // FAILS — same reason as mock A. The val is file-scoped, not instance-scoped.
                // Every SkeClient instance shares the same frozen logger.
                verify(exactly = 1) { mockLoggerB.info(any<() -> Unit>()) }
            } finally {
                unmockkObject(KotlinLogging)
                clearMocks(mockLoggerB)
            }
        }
    })
