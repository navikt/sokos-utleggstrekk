package no.nav.sokos.utleggstrekk.mockexamples

/*
 * Self-contained demonstration that accessing an object OR instantiating a class with
 * `private val logger` in one spec freezes that logger for ALL subsequent specs in the
 * same JVM.
 *
 * No database, no DBListener, no external dependencies — just plain Kotlin objects/classes.
 *
 * ── Why it freezes across specs ───────────────────────────────────────────────
 *
 * `private val logger = KotlinLogging.logger { }` at file scope compiles into the static
 * initialiser of the generated `<FileName>Kt` class. The JVM runs a static initialiser
 * exactly ONCE per ClassLoader — the first time ANYTHING in that file is referenced,
 * whether that is:
 *   - calling a method on a file-level `object`  (LocalService.doWork())
 *   - constructing an instance of a file-level `class`  (ClassBasedService())
 *
 * Both trigger the same static initialiser, which captures the val from whatever
 * KotlinLogging.logger {} returns at that exact moment. Every subsequent spec shares
 * the same ClassLoader, so they all see the same already-frozen reference.
 *
 * ── Structure ─────────────────────────────────────────────────────────────────
 *
 * Part 1 — object (LocalService):
 *   ValLoggerFreezerSpec (order 1) — calls LocalService.doWork() with NO mock → val frozen
 *   ValLoggerVictimSpec  (order 2) — installs mock, calls doWork() → mock never called ❌
 *
 * Part 2 — class (ClassBasedService, mirrors SkeClient):
 *   ClassFreezerSpec     (order 3) — constructs ClassBasedService() with NO mock → val frozen
 *   ClassVictimSpec      (order 4) — installs mock, constructs + calls service → mock never called ❌
 */

import io.kotest.core.spec.Order
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkObject
import io.mockk.verify
import mu.KLogger
import mu.KotlinLogging

// ── Part 1: object ────────────────────────────────────────────────────────────
// Mirrors Repository.kt / any file with a top-level object and a file-level val logger.

private val logger = KotlinLogging.logger { }

object LocalService {
    fun doWork() {
        logger.info { "LocalService is doing work" }
    }
}

// ── Part 2: class ─────────────────────────────────────────────────────────────
// Mirrors SkeClient.kt — a file-level val logger shared by a regular class.
// The val is NOT inside the class; it is file-scoped, compiled into the static
// initialiser of ClassBasedServiceKt. Constructing ANY instance of ClassBasedService
// is enough to trigger that static initialiser and freeze the val.

private val classLogger = KotlinLogging.logger { }

class ClassBasedService {
    fun doWork() {
        classLogger.info { "ClassBasedService is doing work" }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Part 1 specs — object
// ═════════════════════════════════════════════════════════════════════════════

@Order(1)
class ValLoggerFreezerSpec :
    FunSpec({
        test("calls LocalService with no mock active — freezes val logger to the real logger") {
            LocalService.doWork()
            1 shouldBe 1
        }
    })

@Order(2)
class ValLoggerVictimSpec :
    FunSpec({
        val mockLogger =
            mockk<KLogger>(relaxed = true) {
                every { info(any<() -> Unit>()) } just runs
            }

        beforeSpec {
            mockkObject(KotlinLogging)
            every { KotlinLogging.logger(any<() -> Unit>()) } returns mockLogger
        }

        afterSpec {
            unmockkObject(KotlinLogging)
            clearAllMocks()
        }

        test("mockLogger installed in beforeSpec never receives calls — val was frozen by ValLoggerFreezerSpec") {
            LocalService.doWork()

            // FAILS: val was frozen in spec 1 — mockLogger receives 0 calls.
            verify(exactly = 1) { mockLogger.info(any<() -> Unit>()) }
        }

        test("a logger obtained via KotlinLogging.logger {} NOW returns the mock — new calls work") {
            val freshLogger = KotlinLogging.logger {}
            freshLogger.info { "called via fresh logger" }

            // PASSES: freshLogger is resolved after mockkObject is active.
            verify(exactly = 1) { mockLogger.info(any<() -> Unit>()) }
        }
    })

// ═════════════════════════════════════════════════════════════════════════════
// Part 2 specs — class (mirrors SkeClient)
// ═════════════════════════════════════════════════════════════════════════════

// Spec 3: simply constructs ClassBasedService with no mock active.
// This is enough to trigger the static initialiser for ClassBasedServiceKt,
// freezing `private val classLogger` to the real logger permanently.

@Order(3)
class ClassFreezerSpec :
    FunSpec({
        test("constructs ClassBasedService with no mock — freezes classLogger to the real logger") {
            // Just constructing the class is sufficient.
            // The file-level val is evaluated when the JVM first loads this file,
            // which happens here — before ClassVictimSpec has a chance to install a mock.
            val service = ClassBasedService()
            service.doWork()

            1 shouldBe 1
        }
    })

// Spec 4: installs a mock and constructs a NEW ClassBasedService instance.
// Even though each test creates its own instance, `private val classLogger` is
// file-scoped — it is shared across ALL instances and was already frozen in spec 3.
// Creating a new instance does NOT re-run the file-level val initialiser.

@Order(4)
class ClassVictimSpec :
    FunSpec({
        val mockLogger =
            mockk<KLogger>(relaxed = true) {
                every { info(any<() -> Unit>()) } just runs
            }

        beforeSpec {
            mockkObject(KotlinLogging)
            every { KotlinLogging.logger(any<() -> Unit>()) } returns mockLogger
        }

        afterSpec {
            unmockkObject(KotlinLogging)
            clearAllMocks()
        }

        test("new ClassBasedService() does NOT re-evaluate classLogger — val still frozen from spec 3") {
            // A brand-new instance — but the file-level val was already captured in spec 3.
            // This is the key difference from an instance-scoped logger: every instance
            // shares the exact same frozen val reference.
            val service = ClassBasedService()
            service.doWork()

            // FAILS: classLogger was frozen to the real logger in ClassFreezerSpec.
            // mockLogger receives 0 calls regardless of how many new instances are created.
            verify(exactly = 1) { mockLogger.info(any<() -> Unit>()) }
        }

        test("a logger obtained via KotlinLogging.logger {} NOW returns the mock — new calls work") {
            val freshLogger = KotlinLogging.logger {}
            freshLogger.info { "called via fresh logger" }

            // PASSES: freshLogger is resolved after mockkObject is active.
            // If ClassBasedService used `private fun classLogger()` instead of `private val`,
            // service.doWork() would also pass — it would resolve the logger here, not at class-load.
            verify(exactly = 1) { mockLogger.info(any<() -> Unit>()) }
        }
    })
