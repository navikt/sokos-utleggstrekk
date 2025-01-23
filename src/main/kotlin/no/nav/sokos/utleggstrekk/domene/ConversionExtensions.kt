package no.nav.sokos.utleggstrekk.domene

import no.nav.sokos.utleggstrekk.database.model.TrekkPeriodeTable
import no.nav.sokos.utleggstrekk.database.model.TrekkpaleggTable
import no.nav.sokos.utleggstrekk.domene.nav.Aksjonskode
import no.nav.sokos.utleggstrekk.domene.nav.Document
import no.nav.sokos.utleggstrekk.domene.nav.InnrapporteringTrekk
import no.nav.sokos.utleggstrekk.domene.nav.Periode
import no.nav.sokos.utleggstrekk.domene.nav.TrekkAlternativ
import no.nav.sokos.utleggstrekk.domene.nav.TrekkTilOppdrag

    fun TrekkPeriodeTable.toTrekkDokumentPeriode() =
        Periode(
            periodeFomDato = this.datoStart,
            periodeTomDato = this.datoSlutt,
            sats = this.trekkbelop ?: this.trekkprosent!!
        )

    fun TrekkpaleggTable.toTrekkDokument(periodeTableList: List<TrekkPeriodeTable>): TrekkTilOppdrag {
        return TrekkTilOppdrag(
            document = Document(
                transaksjonsId = this.trekkidNav ?: "",
                innrapporteringTrekk = InnrapporteringTrekk(
                    aksjonskode = Aksjonskode.getAksjonskodeForTrekk(this),
                    kreditorIdTss = this.orgnummer,
                    kreditorTrekkId = this.trekkidSke,
                    debitorId = this.skyldner,
                    kodeTrekkAlternativ = TrekkAlternativ.LOPD,
                    kid = this.kid,
                    kreditorsRef = this.saksnummer,
                    kilde = "?",
                    saldo = 0.0,
                    prioritetFomDato = this.opprettetSke,
                    perioder = periodeTableList.map {
                        it.toTrekkDokumentPeriode()
                    }
                )
            )
        )
    }
