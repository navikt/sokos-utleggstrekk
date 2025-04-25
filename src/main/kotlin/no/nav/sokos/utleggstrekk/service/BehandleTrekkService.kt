package no.nav.sokos.utleggstrekk.service

import no.nav.sokos.utleggstrekk.database.model.TrekkPeriodeTable
import no.nav.sokos.utleggstrekk.database.model.UtleggstrekkTable
import no.nav.sokos.utleggstrekk.domene.nav.TrekkTilOppdrag
import no.nav.sokos.utleggstrekk.utils.toTrekkDokument

class BehandleTrekkService(
    private val databaseService: DatabaseService,
) {

    fun lagTrekkSomSkalSendes(): List<Pair<UtleggstrekkTable, List<TrekkTilOppdrag>>> {
        val trekkIkkeSendt = databaseService.hentAlleTrekkSomIkkeErSendt()
        return trekkIkkeSendt.map { trekk ->

            val trekkAlternativMap = databaseService.hentAllePerioderForTrekkVersjon(trekk).groupBy { it.trekkAlternativ }
            val historiskTrekkAlternativMap = databaseService.hentAllePerioderForTrekkId(trekk).groupBy { it.trekkAlternativ }

            val trekkDokumenter = utledAlleDuplikateTrekkPerioder(trekkAlternativMap, historiskTrekkAlternativMap).map {
                trekk.toTrekkDokument(it)
            }
            trekk to trekkDokumenter
        }
    }

    private fun utledAlleDuplikateTrekkPerioder(
        trekkAlternativMap: Map<String, List<TrekkPeriodeTable>>,
        historiskTrekkAlternativMap: Map<String, List<TrekkPeriodeTable>>
    ): List<List<TrekkPeriodeTable>> {

        return if (historiskTrekkAlternativMap.size < 2) {
            trekkAlternativMap.values.toList()
        } else {
            listOf(
                trekkAlternativMap["LOPP"]!! + trekkAlternativMap["LOPM"]!!.map { it.copy(trekkAlternativ = "LOPP", sats = 0.0) }.sortedBy { it.datoStart },
                trekkAlternativMap["LOPM"]!! + trekkAlternativMap["LOPP"]!!.map { it.copy(trekkAlternativ = "LOPM", sats = 0.0) }.sortedBy { it.datoStart }
            )
        }
    }
}