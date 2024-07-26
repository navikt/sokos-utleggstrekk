package no.nav.sokos.utleggstrekk.service

import no.nav.sokos.utleggstrekk.database.model.MidlertidigStansTable
import no.nav.sokos.utleggstrekk.database.model.TrekkTable

class GenererTrekkService(
    private val databaseService: DatabaseService
) {


    fun lagTrekkTilOsFraMidlertidigeTrekk(trekkListe:List<TrekkTable>): List<TrekkTable> {
        val trekkOgStansListe = trekkListe.map {
            TrekkOgStans(
                it,
                databaseService.hentMidletidigStansForSekvensnr(it.sekvensnr).sortedBy { it.startPeriode }
            )
        }

        val nyeTrekkListe = mutableListOf<TrekkTable>()
        trekkOgStansListe.forEach { tos ->
            var startNeste = tos.trekk.startPeriode
            var i =  1
            nyeTrekkListe.addAll(
                tos.midlertidigStansList.map { ms ->
                    val res = tos.trekk.copy(corrid = "${tos.trekk.corrid}-i", startPeriode = startNeste, sluttPeriode = ms.startPeriode.previousPeriod())
                    startNeste = ms.sluttPeriode.nextPeriod()
                    i += 1
                    res
                }
            )
        }
        return nyeTrekkListe
    }
}

data class TrekkOgStans(
    val trekk: TrekkTable,
    val midlertidigStansList: List<MidlertidigStansTable>
)

