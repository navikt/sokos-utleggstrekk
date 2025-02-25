package no.nav.sokos.utleggstrekk.utils

import no.nav.sokos.utleggstrekk.database.model.TrekkPeriodeTable
import no.nav.sokos.utleggstrekk.database.model.UtleggstrekkTable
import no.nav.sokos.utleggstrekk.domene.nav.TrekkAlternativ

suspend fun oppdaterTrekkMedForskjelligSatstype(trekk: UtleggstrekkTable, perioder: List<TrekkPeriodeTable>):List<UtleggstrekkTable>{
    val antallBeloptrekk = perioder.filter { it.trekkAlternativ == TrekkAlternativ.LOPM.value }.size
    val antallProsenttrekk = perioder.filter { it.trekkAlternativ == TrekkAlternativ.LOPP.value }.size
    val trekkListe = mutableListOf<UtleggstrekkTable>()
    if (antallBeloptrekk > 0) {
        trekkListe.add(trekk.copyWithTrekkAlternativ(TrekkAlternativ.LOPM))
    }
    if (antallProsenttrekk > 0) {
        trekkListe.add(trekk.copyWithTrekkAlternativ(TrekkAlternativ.LOPP))
    }
    return trekkListe
}
