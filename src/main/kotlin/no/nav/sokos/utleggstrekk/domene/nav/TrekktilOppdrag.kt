package no.nav.sokos.utleggstrekk.domene.nav

import java.time.LocalDate

import kotlinx.serialization.Serializable

import no.nav.sokos.utleggstrekk.utils.Validation.isDate
import no.nav.sokos.utleggstrekk.utils.Validation.isNumber
import no.nav.sokos.utleggstrekk.utils.Validation.isSafeText

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

fun TrekkTilOppdrag.validate() {
    dokument.validate()
    mmel?.validate()
}

fun Mmel.validate() {
    require(alvorlighetsgrad.isSafeText()) { "alvorlighetsgrad har ugyldige tegn" }
    if (systemId != null) require(systemId.isSafeText()) { "systemId har ugyldige tegn" }
    if (kodeMelding != null) require(kodeMelding.isSafeText()) { "kodeMelding har ugyldige tegn" }
    if (beskrMelding != null) require(beskrMelding.isSafeText()) { "beskrMelding har ugyldige tegn" }
    if (sqlKode != null) require(sqlKode.isSafeText()) { "sqlKode har ugyldige tegn" }
    if (sqlStateMmel != null) require(sqlStateMmel.isSafeText()) { "sqlStateMmel har ugyldige tegn" }
    if (sqlMelding != null) require(sqlMelding.isSafeText()) { "sqlMelding har ugyldige tegn" }
    if (mqCompletionKode != null) require(mqCompletionKode.isSafeText()) { "mqCompletionKode har ugyldige tegn" }
    if (mqReasonKode != null) require(mqReasonKode.isSafeText()) { "mqReasonKode har ugyldige tegn" }
    if (programId != null) require(programId.isSafeText()) { "programId har ugyldige tegn" }
    if (sectionNavn != null) require(sectionNavn.isSafeText()) { "sectionNavn har ugyldige tegn" }
}

fun Document.validate() {
    require(transaksjonsId.isSafeText()) { "transaksjonsId har ugyldige tegn" }
    innrapporteringTrekk.validate()
}

fun InnrapporteringTrekk.validate() {
    require(navTrekkId.isSafeText()) { "navTrekkId har ugyldige tegn" }
    require(kreditorIdTss.length <= 11 && kreditorIdTss.isNumber()) { "kreditorIdTss er ugydlig" }
    require(kreditorTrekkId.length <= 35 && kreditorTrekkId.isSafeText()) { "kreditorTrekkId er ugyldig" }
    require(kreditorsRef.length <= 30 && kreditorsRef.isSafeText()) { "kreditorsRef er ugyldig" }
    require(debitorId.length == 11 && debitorId.isNumber()) { "debitorId er ugyldig" }
    require(kodeTrekktype.length <= 4 && kodeTrekktype.isSafeText()) { "kodeTrekkType er ugyldig" }
    require(kid.length <= 26 && kid.isSafeText()) { "kid er ugyldig" }
    require(kilde.length <= 11 && kilde.isSafeText()) { "kilde er ugyldig" }
    require(saldo >= 0.0) { "saldo < 0" }
    if (prioritetFomDato != null) require(prioritetFomDato.isDate()) { "prioritetFomDato er ugyldig" }
    if (gyldigTomDato != null) require(gyldigTomDato.isDate()) { "gyldigTomDato er ugyldig" }
    perioder?.validate()
}

fun Perioder.validate() {
    periode.forEach(Periode::validate)
}

fun Periode.validate() {
    require(periodeFomDato.isDate()) { "periodeFomDato er ugyldig" }
    require(sats >= 0) { "sats er ugyldig" }
    if (periodeTomDato != null) {
        val fom = LocalDate.parse(periodeFomDato)
        val tom = LocalDate.parse(periodeTomDato)
        require(fom.isBefore(tom)) { "Periodeintervall er ugyldig" }
    }
}