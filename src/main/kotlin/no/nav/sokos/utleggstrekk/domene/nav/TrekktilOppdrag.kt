package no.nav.sokos.utleggstrekk.domene.nav

import kotlinx.serialization.Serializable
import no.nav.sokos.utleggstrekk.database.model.UtleggstrekkTable
import no.nav.sokos.utleggstrekk.domene.ske.Trekkstatus
import no.nav.sokos.utleggstrekk.domene.ske.TrekkstorrelseForPeriode

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
    val kodeTrekkAlternativ: String,
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
    val sats: Double,
    val kodeTrekkAlternativ: TrekkAlternativ? = null,
)

@Serializable
enum class Aksjonskode(val value: String){
    NY("NY"),
    ENDR("ENDR"),
    OPPH("OPPH"),
    ;
    companion object {
        fun getAksjonskodeForTrekk(utleggstrekkTable: UtleggstrekkTable): Aksjonskode {
            if (utleggstrekkTable.trekkstatus.equals(Trekkstatus.AKTIV.value) && utleggstrekkTable.trekkversjon == 1)
                return NY
            else if (utleggstrekkTable.trekkstatus.equals(Trekkstatus.AVSLUTTET.value))
                return OPPH
            else
                return ENDR
        }
    }
}

//Aksjonskoder er NY, ENDR (endring), KANS (kanseller), OPPH (opphør), ENRS (endring restsaldo).
@Serializable
enum class TrekkAlternativ(val value: String) {
    LOPM("LOPM"),   //   Løpende trekk månedssats
    LOPP("LOPP"),   //   Løpende trekk prosentsats
;
    companion object {
        fun getTrekkAlternativ(periode: TrekkstorrelseForPeriode): TrekkAlternativ {
            if (periode.trekkbeloep != null && periode.trekkprosent == null) return LOPM
            else if (periode.trekkprosent != null && periode.trekkbeloep == null)  return LOPP
            else throw NotImplementedError(
                "Begge felter fra skatt, beløp og prosent, er null eller utfylt, Trekkalternativ kan ikke fylles ut. Kun et av den er gyldige for en periode")
        }
    }
}


