package no.nav.sokos.utleggstrekk.domene.nav

import kotlinx.serialization.Serializable

@Serializable
data class TrekkTilOppdrag(
    val document: Document  ,
)

@Serializable
data class Document(
    val transaksjonsId: String,
    val innrapporteringTrekk: InnrapporteringTrekk,
)

@Serializable
data class InnrapporteringTrekk(
    val aksjonskode: Aksjonskode,
    val navTrekkId: String? = null,
    val kreditorIdTss: String,
    val kreditorTrekkId:String,
    val debitorId: String,
    val kodeTrekktype:String = "KRED",  //TODO få riktig fra Endre bruker denne foreløpig
    val kodeTrekkAlternativ: TrekkAlternativ,
    val kid: String,
    val kreditorsRef: String,
    val kilde: String,
    val saldo: Double,
    val prioritetFomDato: String,
    val gyldigTomDato:String? = null,
    val perioder: List<Periode>,
)

@Serializable
data class Periode(
    val periodeFomDato: String,
    val periodeTomDato: String?,
    val sats: Double
)

@Serializable
enum class Aksjonskode(val value: String){
    NY("NY"),
    ENDR("ENDR"),
    KANS("KANS"),
    OPPH("OPPH"),
    ENRS("ENRS")
}
//Aksjonskoder er NY, ENDR (endring), KANS (kanseller), OPPH (opphør), ENRS (endring restsaldo).
@Serializable
enum class TrekkAlternativ(val value: String) {
    LOPD("LOPD"),   //   Løpende trekk dagsats
    LOPM("LOPM"),   //   Løpende trekk månedssats
    LOPP("LOPP"),   //   Løpende trekk prosentsats
    SALD("SALD"),   //   Saldotrekk dagsats
    SALM("SALM"),   //   Saldotrekk månedssats
    SALP("SALP"),   //   Saldotrekk prosentsats
}
