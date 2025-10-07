package no.nav.sokos.utleggstrekk.domene.nav

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

import no.nav.sokos.utleggstrekk.database.model.UtleggstrekkTable
import no.nav.sokos.utleggstrekk.domene.ske.Trekkstatus.AKTIV
import no.nav.sokos.utleggstrekk.domene.ske.Trekkstatus.AVSLUTTET
import no.nav.sokos.utleggstrekk.domene.ske.TrekkstorrelseForPeriode

@Serializable
data class TrekkTilOppdrag(
    val mmel: Mmel? = null,
    val dokument: Document,
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

// TODO: Fjerne optin set encode default in json config
// TODO: Vi må persistere json fra SKE som den er.
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class InnrapporteringTrekk(
    val aksjonskode: Aksjonskode,
    val navTrekkId: String? = null,
    val kreditorIdTss: String,
    val kreditorTrekkId: String,
    val debitorId: String,
    @EncodeDefault val kodeTrekktype: String = "TRK1",
    val kodeTrekkAlternativ: TrekkAlternativ,
    val kid: String,
    val kreditorsRef: String,
    @EncodeDefault val kilde: String = "SOKOSUTLEGG",
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
    val periodeTomDato: String = "9999-12-31", // TODO: slett default value
    val sats: Double = 0.0,
)

@Serializable
enum class Aksjonskode(val value: String) {
    NY("NY"),
    ENDR("ENDR"),
    OPPH("OPPH"),
    ;

    // TODO: kotlin ==/equals & se om denne skal være her.
    companion object {
        fun getAksjonskodeForTrekk(utleggstrekkTable: UtleggstrekkTable): Aksjonskode {
            if (utleggstrekkTable.trekkstatus == AKTIV && utleggstrekkTable.trekkversjon == 1) {
                return NY
            } else if (utleggstrekkTable.trekkstatus == AVSLUTTET) {
                return OPPH
            } else {
                return ENDR
            }
        }

        fun getAksjonskodeFromValue(value: String?): Aksjonskode? {
            if (value == null) {
                return value
            } else {
                return Aksjonskode.valueOf(value)
            }
        }
    }
}

// Aksjonskoder er NY, ENDR (endring), KANS (kanseller), OPPH (opphør), ENRS (endring restsaldo).
@Serializable
enum class TrekkAlternativ {
    LOPM, //   Løpende trekk månedssats
    LOPP, //   Løpende trekk prosentsats
    ;

    companion object {
        fun getTrekkAlternativ(periode: TrekkstorrelseForPeriode): TrekkAlternativ {
            if (periode.trekkbeloep != null && periode.trekkprosent == null) {
                return LOPM
            } else if (periode.trekkprosent != null && periode.trekkbeloep == null) {
                return LOPP
            } else {
                throw NotImplementedError(
                    "Begge felter fra skatt, beløp og prosent, er null eller utfylt, Trekkalternativ kan ikke fylles ut. Kun et av den er gyldige for en periode",
                )
            }
        }
    }
}
