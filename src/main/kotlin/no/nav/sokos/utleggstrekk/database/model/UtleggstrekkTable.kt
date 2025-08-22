package no.nav.sokos.utleggstrekk.database.model

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import kotlinx.serialization.Serializable
import kotliquery.Row

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
    val trekkstatus: String,
    val kid: String,
    val kontonummer: String,
    val betalingsmottaker: String,
    val corrid: String,
    val status: String,
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
        trekkstatus = row.string("trekkstatus"),
        kid = row.string("kid"),
        kontonummer = row.string("kontonummer"),
        betalingsmottaker = row.string("betalingsmottaker"),
        corrid = row.string("corr_id"),
        status = row.string("status"),
        kvitteringLOPM = row.stringOrNull("kvitteringLOPM"),
        kvitteringLOPP = row.stringOrNull("kvitteringLOPP"),
        tidspunktSendtOs = row.localDateTimeOrNull("tidspunkt_sendt_os")?.toKotlinLocalDateTime(),
        tidspunktSisteStatus = row.localDateTime("tidspunkt_siste_status").toKotlinLocalDateTime(),
        tidspunktOpprettet = row.localDateTime("tidspunkt_opprettet").toKotlinLocalDateTime(),
    )
}


