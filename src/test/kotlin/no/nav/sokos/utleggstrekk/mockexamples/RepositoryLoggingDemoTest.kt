package no.nav.sokos.utleggstrekk.mockexamples

/*
 * # Demonstration: `private val logger` in Repository.kt breaks logger mocking
 *
 * Repository.kt declares its logger as a top-level val:
 *
 *     private val logger = KotlinLogging.logger { }   // ← captured ONCE, at class-load time
 *
 * ## Why the val is frozen — and what actually freezes it
 *
 * DBListenerWithEagerRepository is identical to DBListener except its beforeSpec also
 * calls repository.deleteOldData(). That single extra call triggers the lazy val for
 * DBListenerWithEagerRepository.repository, which:
 *
 *   → constructs Repository(dataSource)
 *   → loads Repository.kt's class file
 *   → evaluates `private val logger = KotlinLogging.logger { }` ← FROZEN HERE
 *
 * Because beforeSpec runs before any test body — and therefore before any
 * mockkObject(KotlinLogging) call — the real logger is captured into the val.
 *
 * ## What insertOldAvsluttetTrekk does NOT do
 *
 * `insertOldAvsluttetTrekk` only accesses DBListenerWithEagerRepository.dataSource
 * (a separate lazy for the HikariDataSource). It never touches .repository and does
 * NOT affect the val.
 *
 * ## Execution order (in isolation)
 *
 *   1. DBListenerWithEagerRepository.beforeSpec
 *        → mocks PropertiesConfig
 *        → calls repository.deleteOldData()
 *             → lazy triggers → Repository class loaded
 *             → `private val logger` = REAL KLogger  ← frozen here
 *   2. Test body starts
 *   3. insertOldAvsluttetTrekk() — inserts via dataSource only, val unaffected
 *   4. mockkObject(KotlinLogging) installed — too late, val already frozen in step 1
 *   5. repository.deleteOldData() — lazy already done, same Repository instance
 *        → logs via frozen val = REAL logger, NOT thisTestsMock
 *
 * ## Consequence
 *
 * verify(exactly = 1) { thisTestsMock.info(...) } fails — the log went to the real logger.
 *
 * ## The fix
 *
 * Use `private fun logger(): KLogger = KotlinLogging.logger { }` in Repository.kt.
 * The logger is resolved at call-time, after mockkObject is active, returning the mock.
 */

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

class RepositoryLoggingDemoTest :
    FunSpec({
        extensions(DBListenerWithEagerRepository)

        // Helper: insert a row that qualifies for deleteOldData() deletion.
        // Rows must have trekkstatus=AVSLUTTET and tidspunkt_opprettet older than 6 months.
        fun insertOldAvsluttetTrekk(trekkid: String) {
            DBListenerWithEagerRepository.dataSource.withTransaction { session ->
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

        test("Repository val logger is NOT the mock we install in this test – verify() would lie") {
            // DBListenerWithEagerRepository.beforeSpec already called repository.deleteOldData(),
            // which loaded Repository.kt's class and froze `private val logger` to the real logger.
            // By the time we reach this test body the val is already set — it cannot be replaced.
            insertOldAvsluttetTrekk("demo-trekk-1")

            val thisTestsMock =
                mockk<KLogger>(relaxed = true) {
                    every { info(any<() -> Unit>()) } just runs
                }
            mockkObject(KotlinLogging)
            every { KotlinLogging.logger(any<() -> Unit>()) } returns thisTestsMock

            try {
                // Any NEW call to KotlinLogging.logger {} returns thisTestsMock ✅
                val freshLogger: KLogger = KotlinLogging.logger {}
                freshLogger shouldBe thisTestsMock

                // deleteOldData() fires logger.info(string) for the deleted row,
                // but the log goes to the frozen val (real logger) — NOT thisTestsMock.
                DBListenerWithEagerRepository.repository.deleteOldData()

                // FAILS: mock received 0 calls — the frozen val bypassed it entirely.
                // If Repository used `private fun logger()` this would pass.
                verify(exactly = 1) { thisTestsMock.info(any<String>()) }
            } finally {
                unmockkObject(KotlinLogging)
                clearAllMocks()
                DBListenerWithEagerRepository.clearDB()
            }
        }
    })
