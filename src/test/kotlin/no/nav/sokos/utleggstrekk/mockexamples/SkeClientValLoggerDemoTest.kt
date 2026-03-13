package no.nav.sokos.utleggstrekk.mockexamples

/*
 * Demonstrates that `private val logger` in SkeClient.kt breaks logger mocking
 * even when using the standard beforeSpec/afterSpec pattern for mock setup.
 *
 * SkeClientFreezerSpec (order 1) loads SkeClient.kt with no KotlinLogging mock active,
 * freezing `private val logger` to the real KLogger permanently.
 *
 * SkeClientVictimSpec (order 2) uses beforeSpec/afterSpec — the exact pattern real tests
 * use — to install and clean up mockkObject(KotlinLogging). Every logger verify still
 * fails because the val was already frozen in the freezer spec.
 *
 * The fix is `private fun logger(): KLogger = KotlinLogging.logger { }` — resolved
 * at call-time, after mockkObject is active, always returning the current mock.
 */

import io.kotest.core.spec.Order
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
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

// ── Spec 1: the FREEZER ───────────────────────────────────────────────────────
// Calls SkeClient normally, with no KotlinLogging mock installed.
// Side effect: loads SkeClientKt's static initialiser, freezing `private val logger`
// to the real KLogger permanently for this JVM run.

@Order(1)
class SkeClientFreezerSpec :
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
            clearMocks(slackService, mockTokenProvider)
        }

        test("uses SkeClient without mocking the logger — loads the class and freezes val logger") {
            val skeClient = SkeClient(getClient(getEngine()), slackService, mockTokenProvider)
            val result = skeClient.hentUtleggstrekkFraSekvensnr(1)
            result.shouldBeEmpty()
            // No logger assertions — this test doesn't care about logging.
            // Its only purpose is to trigger class-loading before SkeClientVictimSpec runs.
        }
    })

// ── Spec 2: the VICTIM ────────────────────────────────────────────────────────
// Uses beforeSpec/afterSpec — the standard pattern real tests use.
// mockkObject(KotlinLogging) is installed before any test runs and cleaned up after all.
// Every logger verify still fails because SkeClientFreezerSpec already froze the val.
//
// Also demonstrates that a per-test mock (different object each time, declared at spec
// level and reset in afterTest) makes no difference: the frozen val in SkeClientKt
// stores a direct object reference that no amount of mock reinstallation can replace.

@Order(2)
class SkeClientVictimSpec :
    FunSpec({
        val slackService = mockk<SlackService>(relaxUnitFun = true)
        val mockTokenProvider =
            mockk<MaskinportenAccessTokenClient> {
                coEvery { getAccessToken() } returns "mock-token"
            }

        // Shared logger mock for the whole spec.
        val mockLogger =
            mockk<KLogger>(relaxed = true) {
                every { info(any<() -> Unit>()) } just runs
            }

        beforeSpec {
            mockkObject(PropertiesConfig, KotlinLogging)
            every { PropertiesConfig.config } returns ApplicationConfig("application-test.conf")
            every { KotlinLogging.logger(any<() -> Unit>()) } returns mockLogger
        }

        afterSpec {
            unmockkObject(PropertiesConfig, KotlinLogging)
            clearMocks(slackService, mockTokenProvider, mockLogger)
        }

        afterTest {
            // Clear recorded calls between tests so each verify is independent.
            clearMocks(mockLogger, answers = false)
        }

        test("mocks logger in beforeSpec — val already frozen, mockLogger never called") {
            // mockkObject is active: KotlinLogging.logger {} now returns mockLogger.
            // But SkeClient's `private val logger` was frozen in SkeClientFreezerSpec.
            // The frozen val still points to the real KLogger — mockLogger receives zero calls.
            val skeClient = SkeClient(getClient(getEngine()), slackService, mockTokenProvider)
            skeClient.hentUtleggstrekkFraSekvensnr(1)

            // FAILS: Verification failed: KLogger(#N).info(...) was not called.
            verify(exactly = 1) { mockLogger.info(any<() -> Unit>()) }
        }

        test("new SkeClient() instance does not re-evaluate the file-level val — still frozen") {
            // A brand-new SkeClient instance — but `private val logger` is file-scoped,
            // shared across ALL instances. Creating a new instance does not re-run the
            // static initialiser. mockLogger still receives zero calls.
            val skeClient = SkeClient(getClient(getEngine()), slackService, mockTokenProvider)
            skeClient.hentUtleggstrekkFraSekvensnr(1)

            // FAILS: same reason as above.
            verify(exactly = 1) { mockLogger.info(any<() -> Unit>()) }
        }
    })
