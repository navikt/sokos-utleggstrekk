@file:OptIn(ExperimentalTime::class)

package no.nav.sokos.utleggstrekk.database

import java.sql.Timestamp
import java.util.UUID

import kotlin.time.ExperimentalTime

import com.zaxxer.hikari.HikariDataSource
import kotliquery.Session
import kotliquery.queryOf
import mu.KotlinLogging

import no.nav.sokos.utleggstrekk.database.model.FeilkodeTable
import no.nav.sokos.utleggstrekk.database.model.TrekkPeriodeTable
import no.nav.sokos.utleggstrekk.database.model.UtleggstrekkStatus
import no.nav.sokos.utleggstrekk.database.model.UtleggstrekkStatus.MOTTATT
import no.nav.sokos.utleggstrekk.database.model.UtleggstrekkStatus.SENDT
import no.nav.sokos.utleggstrekk.database.model.UtleggstrekkTable
import no.nav.sokos.utleggstrekk.domene.nav.TrekkAlternativ
import no.nav.sokos.utleggstrekk.domene.nav.TrekkAlternativ.LOPM
import no.nav.sokos.utleggstrekk.domene.nav.TrekkTilOppdrag
import no.nav.sokos.utleggstrekk.domene.ske.Trekkpaalegg

private val logger = KotlinLogging.logger { }
private const val SKATTEETATEN = "SKATTEETATEN"
private const val MAX_SLUTTDATO = "9999-12-31"

class Repository(private val dataSource: HikariDataSource) {
    fun fetchLastSekvensnr(session: Session): Int = session.single(queryOf("SELECT MAX(sekvensnummer) FROM utleggstrekk")) { it.intOrNull(1) } ?: 0

    fun doesTrekkExist(
        trekkid_ske: String,
        sekvensnummer: Int,
        trekkversjon: Int,
        session: Session,
    ): Boolean =
        session.single(
            queryOf(
                """
                SELECT 1 FROM utleggstrekk 
                WHERE sekvensnummer=:sekvensnummer 
                  AND trekkid_ske=:trekkid_ske 
                  AND trekkversjon=:trekkversjon
                """.trimIndent(),
                mapOf(
                    "sekvensnummer" to sekvensnummer,
                    "trekkid_ske" to trekkid_ske,
                    "trekkversjon" to trekkversjon,
                ),
            ),
        ) { 1 } != null

    fun updateNavTrekkStatus(corrId: String, status: UtleggstrekkStatus, session: Session) =
        session.update(
            queryOf(
                "UPDATE utleggstrekk SET status=:status, tidspunkt_siste_status=NOW() WHERE corr_id=:corrId",
                mapOf("status" to status.name, "corrId" to corrId),
            ),
        )

    fun updateTrekkStatusSentAndDateTimeSentOS(corrId: String, session: Session) =
        session.update(
            queryOf(
                """
                UPDATE utleggstrekk SET status=:status, tidspunkt_siste_status=NOW(), tidspunkt_sendt_os=NOW()
                WHERE corr_id=:corrId
                """.trimIndent(),
                mapOf("status" to SENDT.name, "corrId" to corrId),
            ),
        )

    fun updateKvitteringStatus(
        corrId: String,
        status: UtleggstrekkStatus,
        kvittering: String,
        navTrekkId: String,
        trekkalternativ: TrekkAlternativ,
        session: Session,
    ) {
        val kvitteringAlternativ =
            when (trekkalternativ) {
                LOPM -> "kvitteringLOPM"
                else -> "kvitteringLOPP"
            }

        session.update(
            queryOf(
                """
                update utleggstrekk set status=:status, $kvitteringAlternativ=:kvittering, trekkid_nav=:navTrekkId, tidspunkt_siste_status=NOW()  
                where corr_id=:corrId;
                """.trimIndent(),
                mapOf(
                    "status" to status.name,
                    "kvittering" to kvittering,
                    "navTrekkId" to navTrekkId,
                    "corrId" to corrId,
                ),
            ),
        )
    }

    fun savePerioder(perioder: List<TrekkPeriodeTable>, session: Session) {
        val prepStmt =
            session.createPreparedStatement(
                queryOf(
                    """
                    insert into trekkperiode (
                    sekvensnummer,
                    trekkid_ske,
                    trekkversjon,
                    dato_start, 
                    dato_slutt,
                    sats,
                    trekkalternativ,
                    kilde
                    ) values (?,?,?,?,?,?,?,?)        
                    """.trimIndent(),
                ),
            )
        perioder.forEach { periode ->
            prepStmt.setInt(1, periode.sekvensnummer)
            prepStmt.setString(2, periode.trekkidSke)
            prepStmt.setInt(3, periode.trekkversjon)
            prepStmt.setString(4, periode.datoStart)
            prepStmt.setString(5, periode.datoSlutt)
            prepStmt.setObject(6, periode.sats, java.sql.Types.DOUBLE)
            prepStmt.setString(7, periode.trekkAlternativ.name)
            prepStmt.setString(8, periode.kilde)
            prepStmt.addBatch()
        }
        prepStmt.executeBatch()
    }

    // TODO: Kan kotliquery gjøre det mulig å bruke named parameters i batch?
    fun saveAllNewUtleggstrekk(trekkListe: List<Trekkpaalegg>, session: Session) {
        val prepStmt1 =
            session.createPreparedStatement(
                queryOf(
                    """
                    insert into utleggstrekk (
                    sekvensnummer,
                    trekkid_ske, 
                    trekkversjon, 
                    saksnummer,
                    opprettet_ske, 
                    trekkpliktig, 
                    skyldner, 
                    trekkstatus, 
                    betalingsmottaker,
                    kid, 
                    kontonummer, 
                    corr_id,
                    status
                    ) values (?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """.trimIndent(),
                ),
            )
        val prepStmt2 =
            session.createPreparedStatement(
                queryOf(
                    """
                    insert into trekkperiode (
                    sekvensnummer,
                    trekkid_ske,
                    trekkversjon,
                    dato_start, 
                    dato_slutt,
                    sats,
                    trekkalternativ,
                    kilde
                    ) values (?,?,?,?,?,?,?,?)        
                    """.trimIndent(),
                ),
            )
        trekkListe.forEach { trekk ->
            prepStmt1.setInt(1, trekk.sekvensnummer)
            prepStmt1.setString(2, trekk.trekkid)
            prepStmt1.setInt(3, trekk.trekkversjon)
            prepStmt1.setString(4, trekk.saksnummer)
            prepStmt1.setTimestamp(5, Timestamp(trekk.opprettet.toEpochMilliseconds()))
            prepStmt1.setString(6, trekk.trekkpliktig)
            prepStmt1.setString(7, trekk.skyldner)
            prepStmt1.setString(8, trekk.trekkstatus.name)
            prepStmt1.setString(9, trekk.betalingsinformasjon.betalingsmottaker)
            prepStmt1.setString(10, trekk.betalingsinformasjon.kidnummer)
            prepStmt1.setString(11, trekk.betalingsinformasjon.kontonummer)
            prepStmt1.setString(12, UUID.randomUUID().toString())
            prepStmt1.setString(13, MOTTATT.name)
            prepStmt1.addBatch()
            trekk.trekkstoerrelseForPeriode.forEach { periode ->
                val trekkalternativ = TrekkAlternativ.getTrekkAlternativ(periode)
                val sluttdato = periode.sluttdato ?: MAX_SLUTTDATO
                prepStmt2.setInt(1, trekk.sekvensnummer)
                prepStmt2.setString(2, trekk.trekkid)
                prepStmt2.setInt(3, trekk.trekkversjon)
                prepStmt2.setString(4, periode.startdato)
                prepStmt2.setString(5, sluttdato)
                prepStmt2.setObject(
                    6,
                    periode.trekkbeloep?.trekkbeloep ?: periode.trekkprosent?.trekkprosent,
                    java.sql.Types.DOUBLE,
                )
                prepStmt2.setString(7, trekkalternativ.name)
                prepStmt2.setString(8, SKATTEETATEN)
                prepStmt2.addBatch()
            }
        }
        prepStmt1.executeBatch()
        prepStmt2.executeBatch()
    }

    fun fetchTrekkNotSendt(session: Session): List<UtleggstrekkTable> =
        session.list(
            queryOf("SELECT * FROM utleggstrekk WHERE status=:status ORDER BY sekvensnummer ASC", mapOf("status" to MOTTATT.name)),
        ) { row -> UtleggstrekkTable(row) }

    fun fetchPerioderForTrekkVersion(trekk: UtleggstrekkTable, session: Session): List<TrekkPeriodeTable> =
        session.list(
            queryOf(
                "SELECT * FROM trekkperiode WHERE sekvensnummer=:sekvensnummer AND trekkid_ske=:trekkid_ske AND trekkversjon=:trekkversjon",
                mapOf(
                    "sekvensnummer" to trekk.sekvensnummer,
                    "trekkid_ske" to trekk.trekkidSke,
                    "trekkversjon" to trekk.trekkversjon,
                ),
            ),
        ) { row -> TrekkPeriodeTable(row) }

    fun fetchAllPerioderForTrekk(trekk: UtleggstrekkTable, session: Session): List<TrekkPeriodeTable> =
        session.list(
            queryOf(
                "SELECT * FROM trekkperiode WHERE trekkid_ske=:trekkid_ske",
                mapOf(
                    "trekkid_ske" to trekk.trekkidSke,
                ),
            ),
        ) { row -> TrekkPeriodeTable(row) }

    fun saveFeilkoder(kvittering: TrekkTilOppdrag, session: Session) {
        val prepStatement =
            session.createPreparedStatement(
                queryOf(
                    """
                    insert into feilkoder (
                    kreditor_trekk_id ,
                    corr_id,
                    trekkalternativ,
                    feilkode,
                    beskrivelse
                    ) values (?,?,?,?,?)        
                    """.trimIndent(),
                ),
            )
        prepStatement.setString(1, kvittering.dokument.innrapporteringTrekk.kreditorTrekkId)
        prepStatement.setString(2, kvittering.dokument.transaksjonsId)
        prepStatement.setString(3, kvittering.dokument.innrapporteringTrekk.kodeTrekkAlternativ.name)
        prepStatement.setString(4, kvittering.mmel?.kodeMelding)
        prepStatement.setString(5, kvittering.mmel?.beskrMelding)
        prepStatement.addBatch()

        prepStatement.executeBatch()
    }

    fun findFeilkode(corrId: String, session: Session): FeilkodeTable? =
        session.single(
            queryOf("SELECT * FROM feilkoder WHERE corr_id=:corrId", mapOf("corrId" to corrId)),
        ) { row -> FeilkodeTable(row) }

    fun findTrekkByCorrId(corrId: String, session: Session): UtleggstrekkTable? =
        session.single(
            queryOf("SELECT * FROM utleggstrekk WHERE corr_id=:corrId", mapOf("corrId" to corrId)),
        ) { row -> UtleggstrekkTable(row) }
}