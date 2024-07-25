package no.nav.sokos.utleggstrekk.service

import no.nav.sokos.utleggstrekk.database.model.MidlertidigStansTable
import no.nav.sokos.utleggstrekk.database.model.TrekkTable

class GenererTrekkService(
    private val databaseService: DatabaseService
) {


    fun lagTrekkTilOsFraMidlertidigeTrekk(trekkListe:List<TrekkTable>){
        val trekkOgStansListe = trekkListe.map {
            TrekkOgStans(
                it,
                databaseService.hentMidletidigStansForSekvensnr(it.sekvensnr)
            )
        }

        trekkOgStansListe.forEach { tos ->
            tos.midlertidigStansList.map { ms ->
                tos.trekk.copy(sluttPeriode = ms.startPeriode)
            }
        }
    }
}

data class TrekkOgStans(
    val trekk: TrekkTable,
    val midlertidigStansList: List<MidlertidigStansTable>
)