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

            val trekkAlternativMap = databaseService.hentAllePerioderForTrekkVersjon(trekk).groupBy { it.trekkAlternativ }
            val historiskTrekkAlternativMap = databaseService.hentAllePerioderForTrekkId(trekk).groupBy { it.trekkAlternativ }

            val trekkDokumenter = utledAlleDuplikateTrekkPerioder(trekkAlternativMap, historiskTrekkAlternativMap).map { perioder ->
                databaseService.lagreGenerertePerioder(perioder.filter { it.kilde == EGEN_KILDE })
                trekk.toTrekkDokument(perioder)
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
                trekkAlternativMap["LOPP"]!! + trekkAlternativMap["LOPM"]!!.filterNot { it.sats == 0.0 }.map { it.copy(trekkAlternativ = "LOPP", sats = 0.0, kilde = EGEN_KILDE) }.sortedBy { it.datoStart },
                trekkAlternativMap["LOPM"]!! + trekkAlternativMap["LOPP"]!!.filterNot { it.sats == 0.0 }.map { it.copy(trekkAlternativ = "LOPM", sats = 0.0, kilde = EGEN_KILDE) }.sortedBy { it.datoStart }
            )
        }
    }
}