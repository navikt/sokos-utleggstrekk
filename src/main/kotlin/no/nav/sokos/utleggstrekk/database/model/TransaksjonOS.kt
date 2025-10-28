package no.nav.sokos.utleggstrekk.database.model

import java.time.LocalDateTime

import kotliquery.Row

import no.nav.sokos.utleggstrekk.domene.nav.Aksjonskode
import no.nav.sokos.utleggstrekk.domene.nav.TrekkAlternativ

const val INGEN_TREKK_ID_I_KVITTERING = "Mottok ingen NavTrekkId i kvittering"

data class TransaksjonOS(
    val id: Long,
    val transaksjonsID: String,
    val fraSkattID: Long,
    val navTrekkId: String,
    val transaksjonStatus: TransaksjonsStatus,
    val kvitteringStatus: KvitteringStatus,
    val aksjonskode: Aksjonskode,
    val trekkAlternativ: TrekkAlternativ,
    // TODO:  val periode: Periode,
    val tidspunktSendt: LocalDateTime,
    val tidspunktSisteStatus: LocalDateTime,
) {
    constructor(row: Row) : this(
        id = row.long("id"),
        transaksjonsID = row.string("transaksjon_id"),
        fraSkattID = row.long("fraskatt_id"),
        navTrekkId = row.string("nav_trekk_id"),
        transaksjonStatus = TransaksjonsStatus.valueOf(row.string("transaksjon_status").uppercase()),
        kvitteringStatus = KvitteringStatus.valueOf(row.string("kvittering_status").uppercase()),
        aksjonskode = Aksjonskode.valueOf(row.string("aksjonskode").uppercase()),
        trekkAlternativ = TrekkAlternativ.valueOf(row.string("trekkalternativ").uppercase()),
        tidspunktSendt = row.localDateTime("tidspunkt_sendt"),
        tidspunktSisteStatus = row.localDateTime("tidspunkt_siste_status"),
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
    FEIL,
}
