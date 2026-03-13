package no.nav.sokos.utleggstrekk.mockexamples

/*
 * Demonstrates that accessing DBListener.repository in one spec freezes `private val logger`
 * in Repository.kt for ALL subsequent specs in the same JVM.
 *
 * DBListener is a Kotlin `object` — a JVM singleton. Its `repository` property is a `lazy`:
 *
 *     val repository by lazy { Repository(dataSource) }
 *
 * A lazy val runs its initialiser exactly ONCE per JVM lifetime. The first spec to access
 * `DBListener.repository` triggers that initialiser, which:
 *   → constructs Repository(dataSource)
 *   → loads Repository.kt's class file
 *   → evaluates `private val logger = KotlinLogging.logger { }` ← frozen here, forever
 *
 * Any spec that runs AFTER this point — even if it installs mockkObject(KotlinLogging)
 * in its own beforeSpec — cannot replace the already-captured val. The mock will never
 * receive log calls from Repository.
 *
 * ── Structure ─────────────────────────────────────────────────────────────────
 *
 *  CrossSpecFreezerSpec  (order 1) — accesses DBListener.repository without any logger mock.
 *                                    This freezes the val to the REAL logger.
 *
 *  CrossSpecVictimSpec   (order 2) — installs a logger mock, calls repository.deleteOldData(),
 *                                    and verifies the mock was called. FAILS because the val
 *                                    was already frozen by CrossSpecFreezerSpec.
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
import kotliquery.queryOf
import mu.KLogger
import mu.KotlinLogging

import no.nav.sokos.utleggstrekk.database.withTransaction
import no.nav.sokos.utleggstrekk.listener.DBListener

// ── Spec 1: the FREEZER ───────────────────────────────────────────────────────
// Accesses DBListener.repository with no logger mock active.
// Side effect: triggers the lazy, loads Repository.kt, freezes val logger = REAL logger.
// Any spec running after this in the same JVM inherits the frozen val.

@Order(1)
class CrossSpecFreezerSpec :
    FunSpec({
        extensions(DBListener)

        test("accesses repository with no logger mock — freezes val logger to the real logger") {
            // No mockkObject(KotlinLogging) here — the real logger is active.
            // This is the call that triggers DBListener.repository's lazy for the first time,
            // loading Repository.kt's class and freezing `private val logger`.
            DBListener.repository.deleteOldData()

            // No assertions about logging — this test doesn't care about it.
            // Its only purpose is to load Repository.kt before CrossSpecVictimSpec runs.
            1 shouldBe 1
        }
    })

// ── Spec 2: the VICTIM ────────────────────────────────────────────────────────
// Installs a logger mock in beforeSpec and expects Repository to log through it.
// FAILS — because CrossSpecFreezerSpec already froze the val to the real logger.
// DBListener.repository's lazy is a no-op now; the same Repository instance is returned,
// still holding the real logger in its val.

@Order(2)
class CrossSpecVictimSpec :
    FunSpec({
        extensions(DBListener)

        val mockLogger =
            mockk<KLogger>(relaxed = true) {
                every { info(any<() -> Unit>()) } just runs
            }

        beforeSpec {
            // Install the mock BEFORE tests run — looks correct, but it's too late.
            // DBListener.repository was already initialised by CrossSpecFreezerSpec,
            // and its `private val logger` is already the real logger.
            mockkObject(KotlinLogging)
            every { KotlinLogging.logger(any<() -> Unit>()) } returns mockLogger
        }

        afterSpec {
            unmockkObject(KotlinLogging)
            clearAllMocks()
        }

        fun insertOldAvsluttetTrekk(trekkid: String) {
            DBListener.dataSource.withTransaction { session ->
                session.execute(
                    queryOf(
                        """
                        INSERT INTO fraskatt (trekkid, trekkversjon, sekvensnummer, opprettet, trekkstatus,
                                              skyldner, trekkpliktig, saksnummer, tidspunkt_opprettet)
                        VALUES (:trekkid, 1, 1, '2024-01-01T00:00:00Z', 'AVSLUTTET',
                                '19628198007', '889640782', 'sak-1', NOW() - INTERVAL '7 months')
                        """.trimIndent(),
                        mapOf("trekkid" to trekkid),
                    ),
                )
            }
        }

        test("logger mock installed in beforeSpec is ignored — val was frozen by CrossSpecFreezerSpec") {
            // Insert a row so deleteOldData() actually logs something.
            insertOldAvsluttetTrekk("cross-spec-victim-1")

            // deleteOldData() WILL fire logger.info("Slettet X ..."),
            // but it goes to the frozen real logger — NOT mockLogger.
            DBListener.repository.deleteOldData()

            // FAILS: mockLogger received 0 calls.
            // The frozen val bypasses mockLogger entirely.
            //
            // If Repository used `private fun logger()` instead of `private val logger`,
            // the logger would be resolved here, after mockkObject is active, and this
            // verify would pass.
            verify(exactly = 1) { mockLogger.info(any<String>()) }
        }
    })
