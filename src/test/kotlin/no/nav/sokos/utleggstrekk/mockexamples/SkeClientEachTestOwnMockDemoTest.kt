package no.nav.sokos.utleggstrekk.mockexamples

/*
 * Demonstrates that the "each test owns its mock" pattern is also insufficient
 * when using beforeSpec/afterSpec for setup — even with a fresh mock per test.
 *
 * mockkObject(KotlinLogging) is installed in beforeSpec and torn down in afterSpec,
 * exactly like real tests. The stub is re-pointed at a fresh mock in beforeTest.
 * Yet the second test always fails — because the file-level val in SkeClientKt was
 * frozen to whichever logger was current at first class-load, and no amount of stub
 * reinstallation can replace a stored object reference.
 *
 * Run in isolation: test 1 passes (class loads while mockLoggerA is the stub),
 *                   test 2 fails (val holds mockLoggerA, not mockLoggerB).
 * Run in full suite: both fail (SkeClientFreezerSpec already froze val to real logger).
 */

import io.kotest.core.spec.style.FunSpec
import io.ktor.server.config.ApplicationConfig
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

        // Two separate mock instances — each test will point the KotlinLogging stub at its own.
        val mockLoggerA =
            mockk<KLogger>(relaxed = true) {
                every { info(any<() -> Unit>()) } just runs
            }
        val mockLoggerB =
            mockk<KLogger>(relaxed = true) {
                every { info(any<() -> Unit>()) } just runs
            }

        // mockkObject is installed once for the whole spec — same as real tests do.
        beforeSpec {
            mockkObject(PropertiesConfig, KotlinLogging)
            every { PropertiesConfig.config } returns ApplicationConfig("application-test.conf")
        }

        afterSpec {
            unmockkObject(PropertiesConfig, KotlinLogging)
            clearMocks(slackService, mockTokenProvider, mockLoggerA, mockLoggerB)
        }

        // ── Test 1 ────────────────────────────────────────────────────────────────────────────
        // Points the stub at mockLoggerA before the test.
        // In isolation: SkeClientKt is loaded here for the first time, while mockLoggerA is
        // the active stub → val captures mockLoggerA → verify passes ✅
        // In full suite: SkeClientFreezerSpec already loaded the class → val holds the real
        // logger → verify fails ❌

        beforeTest {
            every { KotlinLogging.logger(any<() -> Unit>()) } returns mockLoggerA
        }

        afterTest {
            clearMocks(mockLoggerA, mockLoggerB, answers = false)
        }

        test("test 1 – own mock (mockLoggerA): receives the info call only if class loads here first") {

            val skeClient = SkeClient(getClient(getEngine()), slackService, mockTokenProvider)
            skeClient.hentUtleggstrekkFraSekvensnr(1)

            // PASSES in isolation (val captured mockLoggerA at first class-load).
            // FAILS in full suite (val was frozen to real logger by SkeClientFreezerSpec).
            verify(exactly = 1) { mockLoggerA.info(any<() -> Unit>()) }
        }

        // ── Test 2 ────────────────────────────────────────────────────────────────────────────
        // Points the stub at mockLoggerB — a completely different object.
        // ALWAYS fails: the val was frozen at first class-load (either to the real logger
        // in the full suite, or to mockLoggerA in isolation). Either way, it is NOT mockLoggerB.
        // Reinstalling the stub in beforeTest cannot update an already-stored val reference.

        test("test 2 – own mock (mockLoggerB): ALWAYS fails – val frozen from test 1, new mock is ignored") {
            every { KotlinLogging.logger(any<() -> Unit>()) } returns mockLoggerB

            val skeClient = SkeClient(getClient(getEngine()), slackService, mockTokenProvider)
            skeClient.hentUtleggstrekkFraSekvensnr(1)

            // ALWAYS FAILS: val holds whatever was captured at first class-load.
            // mockLoggerB is a different object and was never stored in SkeClientKt.logger.
            verify(exactly = 1) { mockLoggerB.info(any<() -> Unit>()) }
        }
    })
