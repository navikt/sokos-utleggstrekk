package no.nav.sokos.utleggstrekk.service

import mu.KotlinLogging
import org.slf4j.MDC

import no.nav.sokos.utleggstrekk.database.model.TrekkPeriodeTable
import no.nav.sokos.utleggstrekk.database.model.UtleggstrekkTable
import no.nav.sokos.utleggstrekk.domene.nav.Aksjonskode
import no.nav.sokos.utleggstrekk.domene.nav.TrekkTilOppdrag
import no.nav.sokos.utleggstrekk.utils.toTrekkDokument

private const val EGEN_KILDE = "SOKOS-UTLEGGSTREKK"

private const val LOPENDE_BELOP = "LOPM"

private const val LOPENDE_PROSENT = "LOPP"

class BehandleTrekkService(private val databaseService: DatabaseService) {
    val logger = KotlinLogging.logger { }

    fun lagTrekkSomSkalSendes(): Map<UtleggstrekkTable, List<TrekkTilOppdrag>> {
        val trekkIkkeSendt = databaseService.hentAlleTrekkSomIkkeErSendt()
        return trekkIkkeSendt.associateWith { trekk ->
            MDC.put("x-correlation-id", trekk.corrid)

            val perioderForTrekkversjonMap = databaseService.hentAllePerioderForTrekkVersjon(trekk).groupBy { it.trekkAlternativ }
            val allePerioderForTrekkMap = databaseService.hentAllePerioderForTrekkId(trekk).groupBy { it.trekkAlternativ }

            val perioderSendesOS = utledAlleDuplikateTrekkPerioder(perioderForTrekkversjonMap, allePerioderForTrekkMap)

            val trekkDokumenter =
                if (perioderSendesOS.isEmpty()) {
                    allePerioderForTrekkMap.keys.map { key -> trekk.toTrekkDokument(emptyList(), trekkAlternativ = key) }
                } else {
                    perioderSendesOS.map { perioder ->
                        databaseService.lagreGenerertePerioder(perioder.filter { it.kilde == EGEN_KILDE })
                        if (trekk.trekkversjon > 1 && allePerioderForTrekkMap[perioder.first().trekkAlternativ]!!.minBy { it.trekkversjon }.trekkversjon == trekk.trekkversjon) {
                            logger.info("Oppretter nytt trekk for ${trekk.trekkidSke}/${trekk.trekkversjon}/${perioder[0].trekkAlternativ}")
                            trekk.toTrekkDokument(perioder, Aksjonskode.NY)
                        } else {
                            trekk.toTrekkDokument(perioder)
                        }
                    }
                }
            trekkDokumenter
        }
    }

    private fun utledAlleDuplikateTrekkPerioder(
        perioderForTrekkversjonMap: Map<String, List<TrekkPeriodeTable>>,
        allePerioderForTrekkMap: Map<String, List<TrekkPeriodeTable>>,
    ): List<List<TrekkPeriodeTable>> {
        if (perioderForTrekkversjonMap.isEmpty()) return emptyList()

        if (allePerioderForTrekkMap.size < 2) {
            logger.info("Trekkid skatt: ${allePerioderForTrekkMap.entries.elementAt(0).value[0].trekkidSke} har kun et trekkalternativ")
            return perioderForTrekkversjonMap.values
                .toList()
                .map { periode -> periode.sortedBy { it.datoStart } }
        }

        logger.info("Trekkid skatt: ${allePerioderForTrekkMap.entries.elementAt(0).value[0].trekkidSke} har to trekkalternativ")
        return if (perioderForTrekkversjonMap.size < 2) {
            val (fraTrekkalternativ, tilTrekkalternativ) = perioderForTrekkversjonMap.utledFraTilTrekkalternativ()
            listOf(
                perioderForTrekkversjonMap[fraTrekkalternativ]!!
                    .filterNot { it.sats == 0.0 }
                    .map { it.copy(trekkAlternativ = tilTrekkalternativ, sats = 0.0, kilde = EGEN_KILDE) }
                    .sortedBy { it.datoStart },
                perioderForTrekkversjonMap[fraTrekkalternativ]!!,
            )
        } else {
            listOf(
                perioderForTrekkversjonMap[LOPENDE_PROSENT]!! +
                    perioderForTrekkversjonMap[LOPENDE_BELOP]!!
                        .filterNot { it.sats == 0.0 }
                        .map { it.copy(trekkAlternativ = LOPENDE_PROSENT, sats = 0.0, kilde = EGEN_KILDE) }
                        .sortedBy { it.datoStart },
                perioderForTrekkversjonMap[LOPENDE_BELOP]!! +
                    perioderForTrekkversjonMap[LOPENDE_PROSENT]!!
                        .filterNot { it.sats == 0.0 }
                        .map { it.copy(trekkAlternativ = LOPENDE_BELOP, sats = 0.0, kilde = EGEN_KILDE) }
                        .sortedBy { it.datoStart },
            )
        }
    }

    private fun Map<String, List<TrekkPeriodeTable>>.utledFraTilTrekkalternativ() = if (this[LOPENDE_PROSENT] == null) LOPENDE_BELOP to LOPENDE_PROSENT else LOPENDE_PROSENT to LOPENDE_BELOP
}
