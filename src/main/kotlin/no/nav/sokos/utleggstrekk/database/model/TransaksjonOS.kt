package no.nav.sokos.utleggstrekk.database.model

import java.time.LocalDateTime

import kotliquery.Row

import no.nav.sokos.utleggstrekk.database.RepositoryNy
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
        transaksjonsID = row.string(RepositoryNy.TransaksjonOsTable.TRANSAKSJONS_ID_COLUMN),
        transaksjonStatus = TransaksjonsStatus.valueOf(row.string(RepositoryNy.TransaksjonOsTable.TRANSAKSJON_STATUS_COLUMN).uppercase()),
        trekkIdSke = row.string(RepositoryNy.TransaksjonOsTable.TREKK_ID_SKE_COLUMN),
        trekkversjon = row.int(RepositoryNy.TransaksjonOsTable.TREKKVERSJON_COLUMN),
        kvitteringStatus = KvitteringStatus.valueOf(row.string(RepositoryNy.TransaksjonOsTable.KVITTERING_STATUS_COLUMN).uppercase()),
        aksjonskode = Aksjonskode.valueOf(row.string(RepositoryNy.TransaksjonOsTable.AKSJONSKODE_COLUMN).uppercase()),
        kreditorIdTss = row.string(RepositoryNy.TransaksjonOsTable.KREDITOR_ID_TSS_COLUMN),
        kreditorTrekkId = row.string(RepositoryNy.TransaksjonOsTable.KREDITOR_TREKK_ID_COLUMN),
        kreditorsref = row.string(RepositoryNy.TransaksjonOsTable.KREDITORSREF_COLUMN),
        debitorId = row.string(RepositoryNy.TransaksjonOsTable.DEBITOR_ID_COLUMN),
        trekkAlternativ = TrekkAlternativ.valueOf(row.string(RepositoryNy.TransaksjonOsTable.TREKK_ALTERNATIV_COLUMN).uppercase()),
        trekktype = row.string(RepositoryNy.TransaksjonOsTable.TREKK_TYPE_COLUMN),
        kid = row.string(RepositoryNy.TransaksjonOsTable.KID_COLUMN),
        kilde = row.string(RepositoryNy.TransaksjonOsTable.KILDE_COLUMN),
        saldo = row.double(RepositoryNy.TransaksjonOsTable.SALDO_COLUMN),
        prioritetFomDato = row.stringOrNull(RepositoryNy.TransaksjonOsTable.PRIORITET_FOM_DATO_COLUMN),
        gyldigTomDato = row.stringOrNull(RepositoryNy.TransaksjonOsTable.GYLDIG_TOM_DATO_COLUMN),
        tidspunktSendt = row.localDateTimeOrNull(RepositoryNy.TransaksjonOsTable.TIDSPUNKT_SENDT_COLUMN),
        tidspunktSisteStatus = row.localDateTimeOrNull(RepositoryNy.TransaksjonOsTable.TIDSPUNKT_SISTE_STATUS_COLUMN),
        documentJson = row.string(RepositoryNy.TransaksjonOsTable.DOKUMENT_JSON_COLUMN),
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

enum class PeriodeStatus {
    IKKE_SENDT,
    SENDT,
}