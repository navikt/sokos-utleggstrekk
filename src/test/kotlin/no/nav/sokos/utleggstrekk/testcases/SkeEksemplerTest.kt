package no.nav.sokos.utleggstrekk.testcases

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

import no.nav.sokos.utleggstrekk.database.model.KvitteringStatus
import no.nav.sokos.utleggstrekk.domene.nav.Document
import no.nav.sokos.utleggstrekk.domene.nav.InnrapporteringTrekk
import no.nav.sokos.utleggstrekk.domene.nav.Perioder
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
                        val trekkpaalegg = jsonConfig.decodeFromString<Trekkpaalegg>(resourceToString("$TEST_DIR/$filename").updateYear())
                        val updatedPeriode =
                            trekkpaalegg.trekkstoerrelseForPeriode.map { trekkstorrelseForPeriode ->
                                trekkstorrelseForPeriode.copy(
                                    startdato = trekkstorrelseForPeriode.startdato.updateStartDato(),
                                    sluttdato = trekkstorrelseForPeriode.sluttdato?.updateSluttDato(),
                                )
                            }

                        DBListener.RepositoryNy.insertTrekkFraSkatt(trekkpaalegg.copy(trekkstoerrelseForPeriode = updatedPeriode))
                        service.behandleTrekk()

                        Then("Produseres resultatet i '${filename.resultatFil()}'") {
                            val transaksjoner = DBListener.RepositoryNy.getTransaksjonerTilOsSomIkkeErSendt()
                            val dokumenter = transaksjoner.map { jsonConfig.decodeFromString<TrekkTilOppdrag>(it.documentJson) }
                            val trekk = dokumenter.map { it.dokument.innrapporteringTrekk }

                            val expected =
                                resourceToString("$TEST_DIR/${filename.resultatFil()}")
                                    .updateYear()
                                    .replace("###AVSLUTNINGSDATO###", idag) // Replace template with today

                            val expectedTrekk =
                                jsonConfig.decodeFromString<Array<DocumentUtenTransaksjonsId>>(expected).map { dokument ->
                                    val trekk = dokument.innrapporteringTrekk
                                    val nyePerioder =
                                        trekk.perioder?.let {
                                            Perioder(
                                                it.periode.map { periode ->
                                                    periode.copy(
                                                        periodeFomDato = periode.periodeFomDato.updateStartDato(),
                                                        periodeTomDato = periode.periodeTomDato?.updateSluttDato(),
                                                    )
                                                },
                                            )
                                        }

                                    //
                                    trekk.copy(perioder = nyePerioder)
                                }

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

private fun String.resultatFil() = replace(".json", "_resultat.json")

private fun String.subIndex(): Int {
    val parts = split('_')
    return parts.getOrNull(1)?.toIntOrNull() ?: 0
}

// Move all 2025 into the future to avoid the rules that skip expired periods.
private fun String.updateYear(): String = replace("2025-", "${LocalDate.now().year + 1}-")

private fun String.updateStartDato(): String = LocalDate.parse(this, DateTimeFormatter.ISO_DATE).withDayOfMonth(1).toString()

private fun String.updateSluttDato(): String = LocalDate.parse(this, DateTimeFormatter.ISO_DATE).run { withDayOfMonth(lengthOfMonth()) }.toString()

//
@Serializable
data class DocumentUtenTransaksjonsId(val innrapporteringTrekk: InnrapporteringTrekk)
