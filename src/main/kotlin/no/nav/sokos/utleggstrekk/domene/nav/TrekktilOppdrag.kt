package no.nav.sokos.utleggstrekk.domene.nav

import kotlinx.serialization.Serializable

// De er på samme format
typealias KvitteringFraOppdrag = TrekkTilOppdrag
typealias DokumentTilOppdrag = Document

data class OSDto(
    val transaksjonID: String,
    val trekkIDSke: String,
    val trekkversjon: Int,
    val innrapporteringTrekk: InnrapporteringTrekk,
    val documentJson: String,
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

const val KODE_TREKKTYPE = "TRK1"
const val KILDE = "SOKOSUTLEGG"

@Serializable
data class InnrapporteringTrekk(
    val aksjonskode: Aksjonskode,
    val navTrekkId: String = "",
    val kreditorIdTss: String,
    val kreditorTrekkId: String,
    val kreditorsRef: String,
    val debitorId: String,
    val kodeTrekktype: String = KODE_TREKKTYPE,
    val kodeTrekkAlternativ: TrekkAlternativ,
    val kid: String,
    val kilde: String = KILDE,
    val saldo: Double = 0.0,
    val prioritetFomDato: String? = null,
    // val gyldigTomDato: String = LocalDate().minusDays(1).toString(),
    val gyldigTomDato: String? = null,
    val perioder: Perioder?,
)

@Serializable
data class Perioder(val periode: List<Periode>)

@Serializable
data class Periode(
    val periodeFomDato: String,
    val periodeTomDato: String?,
    val sats: Double = 0.0,
)

@Serializable
enum class Aksjonskode(val value: String) {
    NY("NY"),
    ENDR("ENDR"),
    OPPH("OPPH"),
}

// Aksjonskoder er NY, ENDR (endring), KANS (kanseller), OPPH (opphør), ENRS (endring restsaldo).
@Serializable
enum class TrekkAlternativ(val value: String) {
    LOPM("M"), //   Løpende trekk månedssats
    LOPP("P"), //   Løpende trekk prosentsats
    ;

    val suffix = name[3]
}