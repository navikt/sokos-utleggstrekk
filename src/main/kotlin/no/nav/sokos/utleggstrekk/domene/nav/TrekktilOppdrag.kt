package no.nav.sokos.utleggstrekk.domene.nav

import java.time.LocalDateTime

import kotlinx.serialization.Serializable

import no.nav.sokos.utleggstrekk.database.model.UtleggstrekkStatus
import no.nav.sokos.utleggstrekk.database.model.UtleggstrekkTable
import no.nav.sokos.utleggstrekk.domene.ske.Trekkstatus.AKTIV
import no.nav.sokos.utleggstrekk.domene.ske.Trekkstatus.AVSLUTTET
import no.nav.sokos.utleggstrekk.domene.ske.TrekkstorrelseForPeriode

// De er på samme format
typealias KvitteringFraOppdrag = TrekkTilOppdrag

data class OSDto(
    val transaksjonsID: String,
    val fraSkattID: Long,
    val aksjonskode: Aksjonskode,
    val trekkAlternativ: TrekkAlternativ,
)

data class Varkuleklasse(
    val id: Long,
    val fraSkattID: Long,
    val status: UtleggstrekkStatus,
    val sendtOs: LocalDateTime,
    val kvitteringStatus: String,
    val aksjonskode: Aksjonskode,
    val belop: Double,
)

@Serializable
data class TrekkTilOppdrag(
    val dokument: Document,
    val mmel: Mmel? = null,
)

@Serializable
data class Mmel(
    val systemId: String? = null,
    val kodeMelding: String? = null,
    val alvorlighetsgrad: String,
    val beskrMelding: String? = null,
    val sqlKode: String? = null,
    val sqlStateMmel: String? = null,
    val sqlMelding: String? = null,
    val mqCompletionKode: String? = null,
    val mqReasonKode: String? = null,
    val programId: String? = null,
    val sectionNavn: String? = null,
)

// TODO: Se på spk-mottak
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
    val kreditorTrekkId: String,
    val debitorId: String,
    val kodeTrekktype: String = "TRK1",
    val kodeTrekkAlternativ: TrekkAlternativ,
    val kid: String,
    val kreditorsRef: String,
    val kilde: String = "SOKOSUTLEGG",
    val saldo: Double = 0.0,
    val prioritetFomDato: String,
    val gyldigTomDato: String? = null,
    val perioder: Perioder,
)

@Serializable
data class Perioder(val periode: List<Periode>)

@Serializable
data class Periode(
    val periodeFomDato: String,
    val periodeTomDato: String,
    val sats: Double = 0.0,
)

@Serializable
enum class Aksjonskode(val value: String) {
    NY("NY"),
    ENDR("ENDR"),
    OPPH("OPPH"),
    ;

    companion object {
        fun getAksjonskodeForTrekk(utleggstrekkTable: UtleggstrekkTable): Aksjonskode =
            if (utleggstrekkTable.trekkstatus == AKTIV && utleggstrekkTable.trekkversjon == 1) {
                NY
            } else if (utleggstrekkTable.trekkstatus == AVSLUTTET) {
                OPPH
            } else {
                ENDR
            }
    }
}

// Aksjonskoder er NY, ENDR (endring), KANS (kanseller), OPPH (opphør), ENRS (endring restsaldo).
@Serializable
enum class TrekkAlternativ {
    LOPM, //   Løpende trekk månedssats
    LOPP, //   Løpende trekk prosentsats
    ;

    val suffix = name[3]

    companion object {
        fun getTrekkAlternativ(periode: TrekkstorrelseForPeriode): TrekkAlternativ =
            if (periode.trekkbeloep != null && periode.trekkprosent == null) {
                LOPM
            } else if (periode.trekkprosent != null && periode.trekkbeloep == null) {
                LOPP
            } else {
                throw NotImplementedError(
                    "Begge felter fra skatt, beløp og prosent, er null eller utfylt, Trekkalternativ kan ikke fylles ut. Kun et av den er gyldige for en periode",
                )
            }
    }
}