package no.nav.sokos.utleggstrekk.testcases

import java.time.LocalDate

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

import no.nav.sokos.utleggstrekk.database.model.KvitteringStatus
import no.nav.sokos.utleggstrekk.domene.nav.Document
import no.nav.sokos.utleggstrekk.domene.nav.InnrapporteringTrekk
import no.nav.sokos.utleggstrekk.domene.nav.TrekkTilOppdrag
import no.nav.sokos.utleggstrekk.domene.ske.Trekkpaalegg
import no.nav.sokos.utleggstrekk.listener.DBListener
import no.nav.sokos.utleggstrekk.service.BehandleTrekkServiceNy
import no.nav.sokos.utleggstrekk.util.idag
import no.nav.sokos.utleggstrekk.util.resourceToString
import no.nav.sokos.utleggstrekk.util.resourceToStringList

/**
 *  Datadriven testcases using files in ske_trekkeksempler  1_foo.json -> 1_foo_result.json.  Run in order 1, 1_1, 1_2, if exists.
 */
const val TEST_DIR = "ske_trekkeksempler"

class SkeEksemplerTest :
    BehaviorSpec({
        extensions(DBListener)

        val jsonConfig =
            Json {
                explicitNulls = false
                encodeDefaults = true
                prettyPrint = true
            }

        val service = BehandleTrekkServiceNy(DBListener.RepositoryNy)

        // Flags transaction_os as SENT and OKed.
        fun simulerOkFraOS(document: Document) {
            DBListener.RepositoryNy.updateTransaksjonSendt(document.transaksjonsId)
            DBListener.RepositoryNy.updateTransaksjon(
                document.transaksjonsId,
                KvitteringStatus.OK,
                document.innrapporteringTrekk.kreditorTrekkId + "navId" + document.innrapporteringTrekk.kodeTrekkAlternativ.suffix,
            )
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
                        val trekkpaalegg = jsonConfig.decodeFromString<Trekkpaalegg>(resourceToString("$TEST_DIR/$filename").updateDates())
                        DBListener.RepositoryNy.insertTrekkFraSkatt(trekkpaalegg)
                        service.behandleTrekk()

                        Then("Produseres resultatet i '${filename.resultatFil()}'") {
                            val transaksjoner = DBListener.RepositoryNy.getTransaksjonerTilOsSomIkkeErSendt()
                            val dokumenter = transaksjoner.map { jsonConfig.decodeFromString<TrekkTilOppdrag>(it.documentJson) }
                            val trekk = dokumenter.map { it.dokument.innrapporteringTrekk }

                            val expected =
                                resourceToString("$TEST_DIR/${filename.resultatFil()}")
                                    .updateDates()
                                    .replace("###AVSLUTNINGSDATO###", idag) // Replace template with today

                            val expectedTrekk = jsonConfig.decodeFromString<Array<DocumentUtenTransaksjonsId>>(expected).map { it.innrapporteringTrekk }

                            withClue("Antall trekk til OS forventes å være ${expectedTrekk.size}") {
                                trekk.size shouldBe expectedTrekk.size
                            }

                            trekk.forEachIndexed { i, trekk ->
                                trekk shouldBe expectedTrekk[i]
                            }

                            // Vi later som vi har fått kvittering fra OS
                            dokumenter.forEach { simulerOkFraOS(it.dokument) }
                        }
                    }
                }
            }
        }
    })

private fun String.resultatFil() = this.replace(".json", "_resultat.json")

private fun String.subIndex(): Int {
    val parts = this.split('_')
    return parts.getOrNull(1)?.toIntOrNull() ?: 0
}

// Move all 2025 into the future to avoid the rules that skip expired periods.
private fun String.updateDates(): String = this.replace("2025-", "${LocalDate.now().year + 1}-")

//
@Serializable
data class DocumentUtenTransaksjonsId(val innrapporteringTrekk: InnrapporteringTrekk)
