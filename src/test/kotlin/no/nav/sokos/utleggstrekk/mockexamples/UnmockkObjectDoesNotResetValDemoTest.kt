package no.nav.sokos.utleggstrekk.mockexamples
/*
 * Demonstrates that unmockkObject(KotlinLogging) does NOT reset a logger reference
 * that was captured while the mock was active.
 *
 * All assertions are made within a single test body using try/finally so that
 * mockkObject/unmockkObject are perfectly scoped and cannot affect other tests.
 *
 * LocalServiceForUnmockk simulates a class with `private val logger = KotlinLogging.logger {}`.
 * Its injected logger (set once via setLogger()) mirrors the frozen-val: the first logger
 * stored is used permanently, just as a file-level val is captured once at class-load time.
 *
 * The key point:
 *   unmockkObject reverses the METHOD INTERCEPTION on KotlinLogging.INSTANCE.
 *   It does NOT go back and update any reference that already captured the mock's return value.
 *   The stored reference is permanent from the moment it is first written.
 */
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkObject
import io.mockk.verify
import mu.KLogger
import mu.KotlinLogging

class UnmockkObjectDoesNotResetValDemoTest :
    FunSpec({
        afterSpec {
            LocalServiceForUnmockk.resetLogger()
        }
        test("unmockkObject restores the factory but does NOT clear an already-stored logger reference") {
            val mockLogger =
                mockk<KLogger>(relaxed = true) {
                    every { info(any<() -> Unit>()) } just runs
                }
            mockkObject(KotlinLogging)
            every { KotlinLogging.logger(any<() -> Unit>()) } returns mockLogger
            try {
                // Simulate a class loading while the mock is active — its val captures mockLogger.
                // setLogger() only accepts the first call (null-guard), mirroring a frozen val.
                LocalServiceForUnmockk.setLogger(mockLogger)
                // Confirm the stored reference calls the mock before unmockkObject.
                LocalServiceForUnmockk.doWork()
                verify(exactly = 1) { mockLogger.info(any<() -> Unit>()) }
                clearMocks(mockLogger, answers = false)
                // Tear down the mock — as a real test's afterSpec would.
                unmockkObject(KotlinLogging)
                // ── After unmockkObject ────────────────────────────────────────────────
                // a) The factory is restored — new calls return a real logger (not the mock).
                val freshLogger = KotlinLogging.logger {}
                // b) The stored reference inside LocalServiceForUnmockk is unchanged.
                //    unmockkObject cannot reach back and update a reference that was
                //    already written — whether it lives in a val, a lazy, or a field.
                LocalServiceForUnmockk.doWork()
                verify(exactly = 1) { mockLogger.info(any<() -> Unit>()) }
                // c) The stored logger and the fresh factory logger are different objects.
                LocalServiceForUnmockk.getLogger() shouldNotBe freshLogger
                // d) The stored logger still satisfies the KLogger interface.
                LocalServiceForUnmockk.getLogger().shouldBeInstanceOf<KLogger>()
            } finally {
                // unmockkObject was already called inside the try block, but wrap
                // defensively in case an assertion failed before it was reached.
                try {
                    unmockkObject(KotlinLogging)
                } catch (_: Exception) {
                }
                clearMocks(mockLogger)
            }
        }
    })
