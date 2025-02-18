package no.nav.sokos.utleggstrekk.domene

import no.nav.sokos.utleggstrekk.database.model.TrekkpaleggPeriodeTable
import no.nav.sokos.utleggstrekk.database.model.TrekkpaleggTable
import no.nav.sokos.utleggstrekk.domene.nav.Aksjonskode
import no.nav.sokos.utleggstrekk.domene.nav.Document
import no.nav.sokos.utleggstrekk.domene.nav.InnrapporteringTrekk
import no.nav.sokos.utleggstrekk.domene.nav.Periode
import no.nav.sokos.utleggstrekk.domene.nav.Perioder
import no.nav.sokos.utleggstrekk.domene.nav.TrekkAlternativ
import no.nav.sokos.utleggstrekk.domene.nav.TrekkTilOppdrag

fun TrekkpaleggPeriodeTable.toTrekkDokumentPeriode() =
    Periode(
            periodeFomDato = this.datoStart,
            periodeTomDato = this.datoSlutt,
            sats = this.trekkbelop ?: this.trekkprosent!!
    )

fun TrekkpaleggTable.toTrekkDokument(periodeTableList: List<TrekkpaleggPeriodeTable>): TrekkTilOppdrag {
    return TrekkTilOppdrag(
        dokument = Document(
            transaksjonsId = this.corrid,
            innrapporteringTrekk = InnrapporteringTrekk(
                aksjonskode = Aksjonskode.getAksjonskodeForTrekk(this),
                kreditorIdTss = this.betalingsmottaker,
                kreditorTrekkId = this.trekkidSke,
                debitorId = this.skyldner,
                kodeTrekkAlternativ = TrekkAlternativ.LOPM,
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
