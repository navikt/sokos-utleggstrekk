package no.nav.sokos.utleggstrekk.service

import no.nav.sokos.utleggstrekk.database.model.MidlertidigStansTable
import no.nav.sokos.utleggstrekk.database.model.TrekkTable

class GenererTrekkService(
    private val databaseService: DatabaseService
) {


    fun lagTrekkTilOsFraMidlertidigeTrekk(trekkListe: List<TrekkTable>): List<TrekkTable> {
        val trekkOgStansListe = trekkListe.map {
            TrekkOgStans(
                it,
                databaseService.hentMidletidigStansForSekvensnr(it.sekvensnr).sortedBy { it.startPeriode }
            )
        }

        val nyeTrekkListe = mutableListOf<TrekkTable>()
        trekkOgStansListe.forEach { tos ->
            var startNeste = "${tos.trekk.startPeriode}-01"
            var i = 1
            if (tos.midlertidigStansList.isNotEmpty()) {
                nyeTrekkListe.addAll(
                    tos.midlertidigStansList.map { ms ->
                        val res = tos.trekk.copy(corrid = "${tos.trekk.corrid}-$i", startPeriode = "$startNeste", sluttPeriode = ms.startPeriode.previousPeriodWithEndDay())
                        startNeste = ms.sluttPeriode.nextPeriodWithStartDay()
                        i += 1
                        res
                    }
                )
                nyeTrekkListe.add(tos.trekk.copy(corrid = "${tos.trekk.corrid}-$i", startPeriode = "$startNeste", sluttPeriode = "${tos.trekk.sluttPeriode.addPeriodEndDay()}"))
            } else {
                with (tos.trekk) {
                    nyeTrekkListe.add(copy(startPeriode ="$startPeriode-01", sluttPeriode = "${sluttPeriode.addPeriodEndDay()}"))
                }
            }
        }
        return nyeTrekkListe
    }
}

data class TrekkOgStans(
    val trekk: TrekkTable,
    val midlertidigStansList: List<MidlertidigStansTable>
)

