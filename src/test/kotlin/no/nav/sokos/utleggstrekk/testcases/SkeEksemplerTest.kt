package no.nav.sokos.utleggstrekk.testcases

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

import no.nav.sokos.utleggstrekk.database.model.KvitteringStatus
import no.nav.sokos.utleggstrekk.database.model.TransaksjonsStatus
import no.nav.sokos.utleggstrekk.domene.nav.Document
import no.nav.sokos.utleggstrekk.domene.nav.InnrapporteringTrekk
import no.nav.sokos.utleggstrekk.domene.ske.Trekkpaalegg
import no.nav.sokos.utleggstrekk.listener.DBListener
import no.nav.sokos.utleggstrekk.service.BehandleTrekkServiceNy
import no.nav.sokos.utleggstrekk.util.dager
import no.nav.sokos.utleggstrekk.util.idag
import no.nav.sokos.utleggstrekk.util.resourceToString
import no.nav.sokos.utleggstrekk.util.resourceToStringList
import no.nav.sokos.utleggstrekk.util.tidligere

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
            DBListener.RepositoryNy.updateTransaksjonStatus(document.transaksjonsId, TransaksjonsStatus.SENDT)
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
                        val trekkpaalegg = jsonConfig.decodeFromString<Trekkpaalegg>(resourceToString("$TEST_DIR/$filename"))
                        DBListener.RepositoryNy.insertTrekkFraSkatt(trekkpaalegg)
                        service.behandleTrekk()

                        Then("Produseres resultatet i '${filename.resultatFil()}'") {
                            val transaksjoner = DBListener.RepositoryNy.getTransaksjonerTilOsSomIkkeErSendt()
                            val dokumenter = transaksjoner.map { jsonConfig.decodeFromString<Document>(it.documentJson) }
                            val trekk = dokumenter.map { it.innrapporteringTrekk }

                            val expected =
                                resourceToString("$TEST_DIR/${filename.resultatFil()}")
                                    .replace("###AVSLUTNINGSDATO###", idag tidligere 1.dager) // Replace template with today-1

                            val expectedTrekk = jsonConfig.decodeFromString<Array<DocumentUtenTransaksjonsId>>(expected).map { it.innrapporteringTrekk }

                            withClue("Antall trekk til OS forventes å være ${expectedTrekk.size}") {
                                trekk.size shouldBe expectedTrekk.size
                            }

                            trekk.forEachIndexed { i, trekk ->
                                trekk shouldBe expectedTrekk[i]
                            }

                            // Vi later som vi har fått kvittering fra OS
                            dokumenter.forEach { simulerOkFraOS(it) }
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

//
@Serializable
data class DocumentUtenTransaksjonsId(val innrapporteringTrekk: InnrapporteringTrekk)
