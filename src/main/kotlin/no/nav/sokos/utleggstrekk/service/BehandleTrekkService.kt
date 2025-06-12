package no.nav.sokos.utleggstrekk.service

import mu.KotlinLogging
import no.nav.sokos.utleggstrekk.database.model.TrekkPeriodeTable
import no.nav.sokos.utleggstrekk.database.model.UtleggstrekkTable
import no.nav.sokos.utleggstrekk.domene.nav.Aksjonskode
import no.nav.sokos.utleggstrekk.domene.nav.TrekkTilOppdrag
import no.nav.sokos.utleggstrekk.utils.toTrekkDokument
import org.slf4j.MDC

private const val EGEN_KILDE = "SOKOS-UTLEGGSTREKK"
class BehandleTrekkService(
    private val databaseService: DatabaseService,
) {

    val logger = KotlinLogging.logger { }
    fun lagTrekkSomSkalSendes(): Map<UtleggstrekkTable, List<TrekkTilOppdrag>> {
        val trekkIkkeSendt = databaseService.hentAlleTrekkSomIkkeErSendt()
        return trekkIkkeSendt.associateWith { trekk ->
            MDC.put("x-correlation-id", trekk.corrid)

            val perioderForTrekkversjonMap = databaseService.hentAllePerioderForTrekkVersjon(trekk).groupBy { it.trekkAlternativ }
            val allePerioderForTrekkMap = databaseService.hentAllePerioderForTrekkId(trekk).groupBy { it.trekkAlternativ }

            val trekkDokumenter = utledAlleDuplikateTrekkPerioder(perioderForTrekkversjonMap, allePerioderForTrekkMap).map { perioder ->
                databaseService.lagreGenerertePerioder(perioder.filter { it.kilde == EGEN_KILDE })
                if (trekk.trekkversjon > 1 && allePerioderForTrekkMap[perioder[0].trekkAlternativ]!!.minBy { it.trekkversjon }.trekkversjon == trekk.trekkversjon) {
                    logger.info("Oppretter nytt trekk for ${trekk.trekkidSke}/${trekk.trekkversjon}/${perioder[0].trekkAlternativ}")
                    trekk.toTrekkDokument(perioder, Aksjonskode.NY)
                } else {
                    trekk.toTrekkDokument(perioder)
                }
            }
            trekkDokumenter
        }
    }

    private fun utledAlleDuplikateTrekkPerioder(
        perioderForTrekkversjonMap: Map<String, List<TrekkPeriodeTable>>,
        allePerioderForTrekkMap: Map<String, List<TrekkPeriodeTable>>
    ): List<List<TrekkPeriodeTable>> {

        return if (allePerioderForTrekkMap.size < 2) {
            logger.info("Trekkid skatt: ${allePerioderForTrekkMap.entries.elementAt(0).value[0].trekkidSke} har kun et trekkalternativ")
            perioderForTrekkversjonMap.values.toList().map { p -> p.sortedBy { it.datoStart } }
        } else {
            logger.info("Trekkid skatt: ${allePerioderForTrekkMap.entries.elementAt(0).value[0].trekkidSke} har to trekkalternativ")
            listOf(
                perioderForTrekkversjonMap["LOPP"]!! + perioderForTrekkversjonMap["LOPM"]!!.filterNot { it.sats == 0.0 }.map { it.copy(trekkAlternativ = "LOPP", sats = 0.0, kilde = EGEN_KILDE) }.sortedBy { it.datoStart },
                perioderForTrekkversjonMap["LOPM"]!! + perioderForTrekkversjonMap["LOPP"]!!.filterNot { it.sats == 0.0 }.map { it.copy(trekkAlternativ = "LOPM", sats = 0.0, kilde = EGEN_KILDE) }.sortedBy { it.datoStart }
            )
        }
    }
}