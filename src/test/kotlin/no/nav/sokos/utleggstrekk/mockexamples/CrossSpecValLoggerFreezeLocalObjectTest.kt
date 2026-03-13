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
 * exactly ONCE per ClassLoader — the first time ANYTHING in that file is referenced.
 *
 * Both `logger` and `classLogger` are in THIS file, so they are BOTH captured the
 * first time this file is loaded — which happens when ValLoggerFreezerSpec (order 1)
 * calls LocalService.doWork(). By the time ClassFreezerSpec (order 3) runs, both vals
 * are already frozen to the real logger.
 *
 * The class-based part (Part 2) demonstrates an additional point: even creating a NEW
 * instance of ClassBasedService does not re-evaluate the file-level val. The val is
 * shared across all instances, frozen forever at first class-load.
 *
 * ── Structure ─────────────────────────────────────────────────────────────────
 *
 * Part 1 — object (LocalService):
 *   ValLoggerFreezerSpec (order 1) — calls LocalService.doWork() with NO mock
 *                                    → BOTH logger AND classLogger are frozen here
 *   ValLoggerVictimSpec  (order 2) — installs mock, calls doWork() → mock never called ❌
 *
 * Part 2 — class (ClassBasedService, mirrors SkeClient):
 *   ClassFreezerSpec     (order 3) — constructs ClassBasedService() with NO mock
 *                                    → classLogger already frozen in order 1, no-op here
 *   ClassVictimSpec      (order 4) — installs mock, creates new instance + calls service
 *                                    → mock never called ❌ (val frozen, new instance irrelevant)
 */

import io.kotest.core.spec.Order
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
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

// ── Part 1: object ────────────────────────────────────────────────────────────

private val logger = KotlinLogging.logger { }

object LocalService {
    fun doWork() {
        logger.info { "LocalService is doing work" }
    }
}

// ── Part 2: class ─────────────────────────────────────────────────────────────
// Mirrors SkeClient.kt — a file-level val logger shared by a regular class.
// NOTE: classLogger is in the SAME file as logger above, so it is frozen at the
// exact same moment — when ValLoggerFreezerSpec first loads this file.

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
        test("calls LocalService with no mock active — freezes BOTH logger and classLogger to the real logger") {
            // First reference to anything in this file → static initialiser runs →
            // both `logger` AND `classLogger` are captured as the real KLogger right now.
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
            clearMocks(mockLogger)
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

@Order(3)
class ClassFreezerSpec :
    FunSpec({
        test("constructs ClassBasedService with no mock — classLogger already frozen in order 1, this confirms it") {
            // classLogger was already frozen when ValLoggerFreezerSpec loaded this file.
            // Constructing ClassBasedService here is a no-op for freezing — it's already done.
            // This spec exists to show that constructing a class is sufficient to trigger the
            // static initialiser IF this file had not been loaded yet.
            val service = ClassBasedService()
            service.doWork()

            1 shouldBe 1
        }
    })

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
            clearMocks(mockLogger)
        }

        test("new ClassBasedService() does NOT re-evaluate classLogger — val still frozen from order 1") {
            // A brand-new instance — but classLogger is file-level, shared across all instances,
            // and was frozen when the file was first loaded in ValLoggerFreezerSpec.
            val service = ClassBasedService()
            service.doWork()

            // FAILS: classLogger was frozen to the real logger.
            // mockLogger receives 0 calls regardless of how many new instances are created.
            verify(exactly = 1) { mockLogger.info(any<() -> Unit>()) }
        }

        test("a logger obtained via KotlinLogging.logger {} NOW returns the mock — new calls work") {
            val freshLogger = KotlinLogging.logger {}
            freshLogger.info { "called via fresh logger" }

            // PASSES: freshLogger resolved at call-time, after mockkObject is active.
            verify(exactly = 1) { mockLogger.info(any<() -> Unit>()) }
        }
    })
