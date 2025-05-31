package no.nav.sokos.utleggstrekk.service

import no.nav.sokos.utleggstrekk.database.model.TrekkPeriodeTable
import no.nav.sokos.utleggstrekk.database.model.UtleggstrekkTable
import no.nav.sokos.utleggstrekk.domene.nav.TrekkTilOppdrag
import no.nav.sokos.utleggstrekk.utils.toTrekkDokument

private const val EGEN_KILDE = "SOKOS-UTLEGGSTREKK"
class BehandleTrekkService(
    private val databaseService: DatabaseService,
) {


    fun lagTrekkSomSkalSendes(): List<Pair<UtleggstrekkTable, List<TrekkTilOppdrag>>> {
        val trekkIkkeSendt = databaseService.hentAlleTrekkSomIkkeErSendt()
        return trekkIkkeSendt.map { trekk ->

            val perioderForTrekkversjonMap = databaseService.hentAllePerioderForTrekkVersjon(trekk).groupBy { it.trekkAlternativ }
            val allePerioderForTrekkMap = databaseService.hentAllePerioderForTrekkId(trekk).groupBy { it.trekkAlternativ }

            val trekkDokumenter = utledAlleDuplikateTrekkPerioder(perioderForTrekkversjonMap, allePerioderForTrekkMap).map { perioder ->
                databaseService.lagreGenerertePerioder(perioder.filter { it.kilde == EGEN_KILDE })
                if (trekk.trekkversjon > 1 && allePerioderForTrekkMap[perioder[0].trekkAlternativ]!!.minBy { it.trekkversjon }.trekkversjon == trekk.trekkversjon) {
                    trekk.toTrekkDokument(perioder, "NY")
                } else {
                    trekk.toTrekkDokument(perioder)
                }
            }
            trekk to trekkDokumenter
        }
    }

    private fun utledAlleDuplikateTrekkPerioder(
        perioderForTrekkversjonMap: Map<String, List<TrekkPeriodeTable>>,
        allePerioderForTrekkMap: Map<String, List<TrekkPeriodeTable>>
    ): List<List<TrekkPeriodeTable>> {

        return if (allePerioderForTrekkMap.size < 2) {
            perioderForTrekkversjonMap.values.toList().map { p -> p.sortedBy { it.datoStart } }
        } else {
            listOf(
                perioderForTrekkversjonMap["LOPP"]!! + perioderForTrekkversjonMap["LOPM"]!!.filterNot { it.sats == 0.0 }.map { it.copy(trekkAlternativ = "LOPP", sats = 0.0, kilde = EGEN_KILDE) }.sortedBy { it.datoStart },
                perioderForTrekkversjonMap["LOPM"]!! + perioderForTrekkversjonMap["LOPP"]!!.filterNot { it.sats == 0.0 }.map { it.copy(trekkAlternativ = "LOPM", sats = 0.0, kilde = EGEN_KILDE) }.sortedBy { it.datoStart }
            )
        }
    }
}