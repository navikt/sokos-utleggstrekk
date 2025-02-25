package no.nav.sokos.utleggstrekk.utils

import no.nav.sokos.utleggstrekk.database.model.TrekkPeriodeTable
import no.nav.sokos.utleggstrekk.database.model.UtleggstrekkTable
import no.nav.sokos.utleggstrekk.domene.nav.Aksjonskode
import no.nav.sokos.utleggstrekk.domene.nav.Document
import no.nav.sokos.utleggstrekk.domene.nav.InnrapporteringTrekk
import no.nav.sokos.utleggstrekk.domene.nav.Periode
import no.nav.sokos.utleggstrekk.domene.nav.Perioder
import no.nav.sokos.utleggstrekk.domene.nav.TrekkAlternativ
import no.nav.sokos.utleggstrekk.domene.nav.TrekkTilOppdrag

fun TrekkPeriodeTable.toTrekkDokumentPeriode() =
    Periode(
            periodeFomDato = this.datoStart,
            periodeTomDato = this.datoSlutt,
            sats = this.sats
    )

fun UtleggstrekkTable.toTrekkDokument(periodeTableList: List<TrekkPeriodeTable>): TrekkTilOppdrag {
    if (trekkidSkeOS.isNullOrBlank() || trekkAlternativ.isNullOrBlank()) {
        throw RuntimeException("Kan ikke lage OS trekkdokument uten trekkidkombinasjonen trekkidSke og trekkalternativ")
    }
    return TrekkTilOppdrag(
        dokument = Document(
            transaksjonsId = this.corrid,
            innrapporteringTrekk = InnrapporteringTrekk(
                aksjonskode = Aksjonskode.getAksjonskodeForTrekk(this),
                kreditorIdTss = this.betalingsmottaker,
                kreditorTrekkId = this.trekkidSkeOS,
                debitorId = this.skyldner,
                kodeTrekkAlternativ = this.trekkAlternativ,
                kid = this.kid,
                kreditorsRef = this.saksnummer,
                kilde = "SOKOSUTLEGG",
                saldo = 0.0,
                prioritetFomDato = this.opprettetSke.toLocalDate().toString(),
                perioder = Perioder(
                    periode = periodeTableList.map {
                        it.toTrekkDokumentPeriode()
                    })
            )
        )
    )
}

suspend fun UtleggstrekkTable.copyWithTrekkAlternativ(alternativ: TrekkAlternativ):UtleggstrekkTable {
    return this.copy(trekkAlternativ = alternativ.value, trekkidSkeOS = "$trekkidSke-${alternativ.value.last()}")
}

