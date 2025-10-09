package no.nav.sokos.utleggstrekk.database.model

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import kotlinx.serialization.Serializable

import kotliquery.Row

import no.nav.sokos.utleggstrekk.domene.nav.TrekkAlternativ
import no.nav.sokos.utleggstrekk.domene.ske.Trekkstatus

@Serializable
data class UtleggstrekkTable(
    val utleggstrekkTableId: Long,
    val trekkidNav: String? = null,
    val sekvensnummer: Int,
    val saksnummer: String,
    val trekkidSke: String,
    val trekkversjon: Int,
    val opprettetSke: LocalDateTime,
    val trekkpliktig: String,
    val skyldner: String,
    val trekkstatus: Trekkstatus,
    val kid: String,
    val kontonummer: String,
    val betalingsmottaker: String,
    val corrid: String,
    val status: UtleggstrekkStatus,
    val kvitteringLOPM: String? = null,
    val kvitteringLOPP: String? = null,
    val tidspunktSendtOs: LocalDateTime? = null,
    val tidspunktSisteStatus: LocalDateTime,
    val tidspunktOpprettet: LocalDateTime,
) {
    constructor(row: Row) : this(
        utleggstrekkTableId = row.long("id"),
        trekkidNav = row.stringOrNull("trekkid_nav"),
        sekvensnummer = row.int("sekvensnummer"),
        saksnummer = row.string("saksnummer"),
        trekkidSke = row.string("trekkid_ske"),
        trekkversjon = row.int("trekkversjon"),
        opprettetSke = row.localDateTime("opprettet_ske").toKotlinLocalDateTime(),
        trekkpliktig = row.string("trekkpliktig"),
        skyldner = row.string("skyldner"),
        trekkstatus = Trekkstatus.valueOf(row.string("trekkstatus").uppercase()),
        kid = row.string("kid"),
        kontonummer = row.string("kontonummer"),
        betalingsmottaker = row.string("betalingsmottaker"),
        corrid = row.string("corr_id"),
        status = UtleggstrekkStatus.valueOf(row.string("status").uppercase()),
        kvitteringLOPM = row.stringOrNull("kvitteringLOPM"),
        kvitteringLOPP = row.stringOrNull("kvitteringLOPP"),
        tidspunktSendtOs = row.localDateTimeOrNull("tidspunkt_sendt_os")?.toKotlinLocalDateTime(), // TODO: java localdatetime
        tidspunktSisteStatus = row.localDateTime("tidspunkt_siste_status").toKotlinLocalDateTime(), // TODO: er dette tidspunkt for kvittering?
        tidspunktOpprettet = row.localDateTime("tidspunkt_opprettet").toKotlinLocalDateTime(), // TODO:
    )
}

fun UtleggstrekkTable.trekkIdWithSuffix(trekkAlternativ: TrekkAlternativ) = "${trekkidSke}${trekkAlternativ.suffix}"

enum class UtleggstrekkStatus(val status: String) {
    MOTTATT("MOTTATT"),
    SENDT("SENDT"),

    KVITTERING_OK("KVITTERING_OK"),
    KVITTERING_FEILET("KVITTERING_FEILET"),
}
