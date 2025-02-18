package no.nav.sokos.utleggstrekk.domene.nav

import kotlinx.serialization.Serializable
import no.nav.sokos.utleggstrekk.database.model.TrekkpaleggTable
import no.nav.sokos.utleggstrekk.domene.ske.Trekkstatus

@Serializable
data class TrekkTilOppdrag(
    val mmel: Mmel? = null,
    val dokument: Document,
)

@Serializable
data class Mmel(
    val systemId: String?,
    val kodeMelding: String?,
    val alvorlighetsgrad: String,
    val beskrMelding: String?,
    val sqlKode: String?,
    val sqlStateMmel: String?,
    val sqlMelding: String?,
    val mqCompletionKode: String?,
    val mqReasonKode: String?,
    val programId: String?,
    val sectionNavn: String?,
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
    val kilde: String = "SOKOSUTLEGG",
    val saldo: Double,
    val prioritetFomDato: String,
    val gyldigTomDato:String? = null,
    val perioder: Perioder,
)

@Serializable
data class Perioder(
    val periode: List<Periode>
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
    ENRS("ENRS"),
    ;
    companion object {
        fun getAksjonskodeForTrekk(trekkpaleggTable: TrekkpaleggTable): Aksjonskode {
            if (trekkpaleggTable.trekkstatus.equals(Trekkstatus.AKTIV.value) && trekkpaleggTable.trekkversjon == 1)
                return NY
            else if (trekkpaleggTable.trekkstatus.equals(Trekkstatus.AVSLUTTET.value))
                return OPPH
            else
                return ENDR
        }
    }

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


