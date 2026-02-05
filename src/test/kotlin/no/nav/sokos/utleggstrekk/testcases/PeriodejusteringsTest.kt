package no.nav.sokos.utleggstrekk.testcases

import java.time.LocalDate

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

import no.nav.sokos.utleggstrekk.config.jsonConfig
import no.nav.sokos.utleggstrekk.database.model.KvitteringStatus
import no.nav.sokos.utleggstrekk.domene.nav.Document
import no.nav.sokos.utleggstrekk.domene.nav.TrekkTilOppdrag
import no.nav.sokos.utleggstrekk.domene.ske.Trekkpaalegg
import no.nav.sokos.utleggstrekk.listener.DBListener
import no.nav.sokos.utleggstrekk.service.BehandleTrekkServiceNy
import no.nav.sokos.utleggstrekk.util.resourceToString
import no.nav.sokos.utleggstrekk.util.resourceToStringList

/**
 *  Datadriven testcases using files in periodejustering_eksempler
 */

class PeriodejusteringsTest :
    BehaviorSpec({
        extensions(DBListener)

        // Flags transaction_os as SENT and OKed.
        fun simulerOkFraOS(document: Document) {
            DBListener.RepositoryNy.updateTransaksjonSendt(document.transaksjonsId)
            DBListener.RepositoryNy.updateReceiptStatusOfTransaksjon(
                document.transaksjonsId,
                KvitteringStatus.OK,
                document.innrapporteringTrekk.kreditorTrekkId + "navId" + document.innrapporteringTrekk.kodeTrekkAlternativ.suffix,
            )
        }

        val service by lazy {
            BehandleTrekkServiceNy(DBListener.RepositoryNy)
        }

        val fileNames = resourceToStringList(TEST_DIR).filterNot { it.contains("resultat") }.filter { it.endsWith(".json") }
        val tests = fileNames.groupBy { it.substringBefore("_").toInt() } // Group files by first number.
        val numberOfTests: Int = tests.size

        (1..numberOfTests).forEach { i ->
            val testFiles = tests[i]!!.sortedBy { name -> name.subIndex() } // Sort files by second number, first if no second number.

            Given("Testcase '$i'") {
                DBListener.clearDB()
                testFiles.forEach { filename ->
                    When("Filen '$filename' prosesseres") {
                        val trekkpaalegg = jsonConfig.decodeFromString<Trekkpaalegg>(resourceToString("$TEST_DIR/$filename").updateYear())

                        DBListener.RepositoryNy.insertTrekkFraSkatt(trekkpaalegg)
                        service.behandleTrekk()

                        Then("Produseres resultatet i '${filename.resultatFil()}'") {
                            val transaksjoner = DBListener.RepositoryNy.getTransaksjonerTilOsSomIkkeErSendt()
                            val dokumenter = transaksjoner.map { jsonConfig.decodeFromString<TrekkTilOppdrag>(it.documentJson) }
                            val trekk = dokumenter.map { it.dokument.innrapporteringTrekk }

                            val expected = resourceToString("$TEST_DIR/${filename.resultatFil()}").updateYear()
                            val expectedTrekk = jsonConfig.decodeFromString<Array<DocumentUtenTransaksjonsId>>(expected).map { it.innrapporteringTrekk }

                            withClue("Antall trekk til OS forventes å være ${expectedTrekk.size}. $trekk forskjellig fra $expectedTrekk") {
                                trekk.size shouldBe expectedTrekk.size
                            }

                            withClue("Trekkene skal være de samme") {
                                trekk.forEachIndexed { i, trekk -> trekk shouldBe expectedTrekk[i] }
                            }

                            // Vi later som vi har fått kvittering fra OS
                            dokumenter.forEach { simulerOkFraOS(it.dokument) }
                        }
                    }
                }
            }
        }
    }) {
    companion object {
        const val TEST_DIR = "periodejustering_eksempler"
    }
}

private fun String.resultatFil() = replace(".json", "_resultat.json")

private fun String.subIndex(): Int {
    val parts = split('_')
    return parts.getOrNull(1)?.toIntOrNull() ?: 0
}

// Move all 2025 into the future to avoid the rules that skip expired periods.
private fun String.updateYear(): String = replace("2025-", "${LocalDate.now().year + 1}-")