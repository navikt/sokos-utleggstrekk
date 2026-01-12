package no.nav.sokos.utleggstrekk.database.model

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class RemapFomTomTest :
    BehaviorSpec({
        val testData =
            mapOf(
                listOf(periode("2015-01-05")) to listOf(periode("2015-01-01")), // Periode uten tom
                listOf(periode("2015-01-05", tom = "2015-01-06")) to listOf(periode("2015-01-01", "2015-01-31")), // januarperiode
                listOf(periode("2015-02-05", tom = "2015-02-06")) to listOf(periode("2015-02-01", "2015-02-28")), // februarperiode
                listOf(periode("2015-02-05", tom = "2015-02-06"), periode("2015-02-10", "2015-02-20")) to listOf(periode("2015-02-01", "2015-02-28")), // periode 2 overskriver periode 1
                listOf(periode("2015-01-20", tom = "2015-02-04"), periode(fom = "2015-02-05")) to listOf(periode("2015-01-01", "2015-01-31"), periode("2015-02-01")), // lukket og åpen periode
            )

        testData.forEach { (foer, etter) ->
            Given("Perioder ${foer.format()}") {
                Then("Blir justert til ${etter.format()}") {
                    foer.mapNewFomTom().format() shouldBe etter.format()
                }
            }
        }
    })

private fun List<PeriodeFraSkatt>.format() = joinToString(", ") { it.formatPeriode() }

private fun PeriodeFraSkatt.formatPeriode() =
    buildString {
        append('[')
        append(startdato)
        append(", ")
        if (sluttdato != null) {
            append(sluttdato)
            append("]")
        } else {
            append(">")
        }
    }

private fun periode(fom: String, tom: String? = null): PeriodeFraSkatt = PeriodeFraSkatt(0, 0, "id", fom, tom, 1.0, null)
