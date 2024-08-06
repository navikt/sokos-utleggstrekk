package no.nav.sokos.utleggstrekk.service

import no.nav.sokos.utleggstrekk.database.model.TrekkTable
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters.lastDayOfMonth

class GenererTrekkService(
    private val databaseService: DatabaseService,
) {
    fun lagTrekkTilOs(trekkListe: List<TrekkTable>): MutableList<TrekkTable> {
        val nyTrekkListe = mutableListOf<TrekkTable>()

        trekkListe.forEach { trekk ->
            val sluttPeriodeForTrekk = LocalDate.parse("${trekk.sluttPeriode}-01").with(lastDayOfMonth())
            var nesteStartPeriode = LocalDate.parse("${trekk.startPeriode}-01")
            val alleMidlertidigStansForTrekk = databaseService.hentMidletidigStansForSekvensnr(trekk.sekvensnr).sortedBy { it.startPeriode }

            alleMidlertidigStansForTrekk.forEachIndexed { index, midlertidigStans ->
                val nySluttPeriode = LocalDate.parse("${midlertidigStans.startPeriode}-01").minusMonths(1).with(lastDayOfMonth())
                val nyttTrekk = trekk.copy(corrid = "${trekk.corrid}-${index + 1}", startPeriode = nesteStartPeriode.toString(), sluttPeriode = nySluttPeriode.toString())
                nesteStartPeriode = LocalDate.parse("${midlertidigStans.sluttPeriode}-01").plusMonths(1)
                nyTrekkListe.add(nyttTrekk)
            }

            val corrid = if (alleMidlertidigStansForTrekk.isEmpty()) trekk.corrid else "${trekk.corrid}-${alleMidlertidigStansForTrekk.size + 1}"
            nyTrekkListe.add(trekk.copy(corrid = corrid, startPeriode = nesteStartPeriode.toString(), sluttPeriode = sluttPeriodeForTrekk.toString()))
        }

        return nyTrekkListe
    }
}