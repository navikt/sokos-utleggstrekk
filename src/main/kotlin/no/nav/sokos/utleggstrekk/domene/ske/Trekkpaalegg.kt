package no.nav.sokos.utleggstrekk.domene.ske

import kotlinx.serialization.Serializable
import no.nav.sokos.utleggstrekk.domene.nav.Aksjonskode
import no.nav.sokos.utleggstrekk.domene.nav.Document
import no.nav.sokos.utleggstrekk.domene.nav.InnrapporteringTrekk
import no.nav.sokos.utleggstrekk.domene.nav.Periode
import no.nav.sokos.utleggstrekk.domene.nav.TrekkAlternativ
import no.nav.sokos.utleggstrekk.domene.nav.TrekkTilOppdrag

@Serializable
data class Trekkpaalegg(
    val trekkid: String,
    val trekkversjon: Int,
    val sekvensnummer: Int,
    val opprettet: String,
    val saksnummer: String,
    val trekkpliktig: String,
    val skyldner: String,
    val trekkstatus: Trekkstatus,
    val trekkstoerrelseForPeriode: List<TrekkstorrelseForPeriode>,
    val betalingsinformasjon: Betalingsinformasjon,
)

@Serializable
data class Trekkprosent(
    val trekkprosent: Double? = null,
)

@Serializable
data class Trekkbeloep(
    val trekkbeloep: Double? = null,
)

@Serializable
enum class Trekkstatus(val value: String) {
    AKTIV("aktiv"),
    AVSLUTTET("avsluttet")
}

@Serializable
data class TrekkstorrelseForPeriode(
    val startdato: String,
    val sluttdato: String?,
    val trekkbeloep: Trekkbeloep?,
    val trekkprosent: Trekkprosent?
)

@Serializable
data class Betalingsinformasjon(
    val betalingsmottaker: String,
    val kidnummer: String,
    val kontonummer: String
)

fun Trekkpaalegg.toTrekkDokument(): TrekkTilOppdrag {
    return TrekkTilOppdrag(
        document = Document(
            transaksjonsId = "??",
            innrapporteringTrekk = InnrapporteringTrekk(
                aksjonskode = Aksjonskode.NY,
                kreditorIdTss = this.betalingsinformasjon.betalingsmottaker,
                kreditorTrekkId = this.saksnummer,
                debitorId = this.skyldner,
                kodeTrekkAlternativ = TrekkAlternativ.LOPD,
                kid = this.betalingsinformasjon.kidnummer,
                kreditorsRef = this.saksnummer,
                kilde = "?",
                saldo = 0.0,
                prioritetFomDato = this.opprettet,
                perioder = this.trekkstoerrelseForPeriode.map {
                    Periode(
                        periodeFomDato = it.startdato,
                        periodeTomDato = it.sluttdato,
                        sats = it.trekkbeloep?.trekkbeloep ?: it.trekkprosent?.trekkprosent!!
                    )
                }
            )
        )
    )
}

fun TrekkstorrelseForPeriode.toTrekkTilOppdragPeriode(): Periode {
    return Periode(
        periodeFomDato = this.startdato,
        periodeTomDato = this.sluttdato ?: "",
        sats = this.trekkbeloep?.trekkbeloep ?: this.trekkprosent!!.trekkprosent!!
    )
}