package no.nav.sokos.utleggstrekk.database.model

import java.time.LocalDateTime

import kotliquery.Row

import no.nav.sokos.utleggstrekk.domene.nav.Aksjonskode
import no.nav.sokos.utleggstrekk.domene.nav.TrekkAlternativ

const val INGEN_TREKK_ID_I_KVITTERING = "Mottok ingen NavTrekkId i kvittering"

data class TransaksjonOS(
    val id: Long,
    val transaksjonsID: String,
    val trekkIdSke: String,
    val trekkversjon: Int,
    val navTrekkId: String,
    val transaksjonStatus: TransaksjonsStatus,
    val kvitteringStatus: KvitteringStatus,
    val aksjonskode: Aksjonskode,
    val kreditorIdTss: String,
    val kreditorTrekkId: String,
    val kreditorsref: String,
    val debitorId: String,
    val trekkAlternativ: TrekkAlternativ,
    val trekktype: String,
    val kid: String,
    val kilde: String,
    val saldo: Double,
    val prioritetFomDato: String?,
    val gyldigTomDato: String?,
    val tidspunktSendt: LocalDateTime?,
    val tidspunktSisteStatus: LocalDateTime?,
    val documentJson: String,
    val perioder: List<PeriodeTilOS>,
) {
    constructor(row: Row, perioder: List<PeriodeTilOS>) : this(
        id = row.long("id"),
        navTrekkId = row.string("nav_trekk_id"),
        transaksjonsID = row.string("transaksjons_id"),
        transaksjonStatus = TransaksjonsStatus.valueOf(row.string("transaksjon_status").uppercase()),
        trekkIdSke = row.string("trekk_id_ske"),
        trekkversjon = row.int("trekkversjon"),
        kvitteringStatus = KvitteringStatus.valueOf(row.string("kvittering_status").uppercase()),
        aksjonskode = Aksjonskode.valueOf(row.string("aksjonskode").uppercase()),
        kreditorIdTss = row.string("kreditor_id_tss"),
        kreditorTrekkId = row.string("kreditor_trekk_id"),
        kreditorsref = row.string("kreditorsref"),
        debitorId = row.string("debitor_id"),
        trekkAlternativ = TrekkAlternativ.valueOf(row.string("trekk_alternativ").uppercase()),
        trekktype = row.string("trekk_type"),
        kid = row.string("kid"),
        kilde = row.string("kilde"),
        saldo = row.double("saldo"),
        prioritetFomDato = row.stringOrNull("prioritet_fom_dato"),
        gyldigTomDato = row.stringOrNull("gyldig_tom_dato"),
        tidspunktSendt = row.localDateTimeOrNull("tidspunkt_sendt"),
        tidspunktSisteStatus = row.localDateTimeOrNull("tidspunkt_siste_status"),
        documentJson = row.string("dokument_json"),
        perioder = perioder,
    )
}

enum class KvitteringStatus {
    IKKE_MOTTATT,
    OK,
    FEIL,
    UKJENT,
    ;

    companion object {
        fun fromValue(value: String?): KvitteringStatus =
            when (value) {
                "00" -> OK
                "04" -> FEIL
                "08" -> FEIL
                else -> UKJENT
            }
    }
}

enum class TransaksjonsStatus {
    IKKE_SENDT,
    SENDT,
}