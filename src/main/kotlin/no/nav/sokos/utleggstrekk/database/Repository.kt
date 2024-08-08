package no.nav.sokos.utleggstrekk.database

import mu.KotlinLogging
import no.nav.sokos.utleggstrekk.database.RepositoryExtensions.getColumn
import no.nav.sokos.utleggstrekk.database.RepositoryExtensions.param
import no.nav.sokos.utleggstrekk.database.RepositoryExtensions.toMidlertidigStans
import no.nav.sokos.utleggstrekk.database.RepositoryExtensions.toTrekkTable
import no.nav.sokos.utleggstrekk.database.RepositoryExtensions.withParameters
import no.nav.sokos.utleggstrekk.database.model.MidlertidigStansTable
import no.nav.sokos.utleggstrekk.database.model.TrekkTable
import no.nav.sokos.utleggstrekk.domene.ske.Utleggstrekk
import java.sql.Connection
import java.util.UUID

private val logger = KotlinLogging.logger { }

object Repository {
    fun Connection.fetchLastSekvensnr(): Int {
        val rs = prepareStatement("""select max(sekvensnr) from trekk""").executeQuery()
        return if (rs.next()) {
            rs.getColumn("sekvensnr")
        } else {
            0
        }
    }

    fun Connection.doesTrekkExist(trekkid_ske: String, sekvensnr: Int, trekkversjon: Int): Boolean =
        prepareStatement(
            """
            select 1 from trekk where sekvensnr = ? and trekkid_ske = ? and trekkversjon = ?
            """.trimIndent(),
        ).withParameters(
            param(sekvensnr),
            param(trekkid_ske),
            param(trekkversjon),
        ).executeQuery()
            .next()

    fun Connection.updateTrekkStatus(corrid: String, status: String) {
        println("oppdaterer status for corrid $corrid")
        val result =
            prepareStatement(
                """
                update trekk set status = ? where corrid = ?;
                """.trimIndent(),
            ).withParameters(
                param(status),
                param(corrid),
            ).executeUpdate()
        commit()
        println("oppdaterte $result rad")
    }

    fun Connection.saveAllNewUtleggstrekk(
        trekkListe: List<Utleggstrekk>,
    ) {
        val prepStmt1 =
            prepareStatement(
                """
                insert into trekk (
                sekvensnr,
                trekkid_ske, 
                trekkversjon, 
                trekkopprettet, 
                trekkpliktig, 
                skyldner, 
                trekkstatus, 
                startperiode, 
                sluttperiode, 
                trekkbelop, 
                trekkprosent, 
                corrid,
                status,
                kid, 
                kontonummer 
                ) values (?,?,?,TO_TIMESTAMP(?, 'YYYY-MM-DD"T"HH24:MI:SS.MSZ'),?,?,?,?,?,?,?,?,'MOTTATT',?,?)
                """.trimIndent(),
            )
        val prepStmt2 =
            prepareStatement(
                """
                insert into midlertidigstans (
                trekksekvensnr, 
                startperiode, 
                sluttperiode
                ) values (?, ?, ?)        
                """.trimIndent(),
            )
        trekkListe.forEach {
            val trekkBelop = it.trekkbeloep?.trekkbeloep
            val trekkProsent = it.trekkprosent?.trekkprosent
            prepStmt1.setInt(1, it.sekvensnummer)
            prepStmt1.setString(2, it.trekkid)
            prepStmt1.setInt(3, it.trekkversjon)
            prepStmt1.setString(4, it.opprettet)
            prepStmt1.setString(5, it.trekkpliktig)
            prepStmt1.setString(6, it.skyldner)
            prepStmt1.setString(7, it.trekkstatus)
            prepStmt1.setString(8, it.startPeriode)
            prepStmt1.setString(9, it.sluttPeriode)
            prepStmt1.setDouble(10, trekkBelop ?: 0.0)
            prepStmt1.setDouble(11, trekkProsent ?: 0.0)
            prepStmt1.setString(12, UUID.randomUUID().toString())
            prepStmt1.setString(13, it.kidnummer)
            prepStmt1.setString(14, it.kontonummer)
            prepStmt1.addBatch()

            if (!it.midlertidigStans.isNullOrEmpty()) {
                it.midlertidigStans.forEach { stans ->
                    prepStmt2.setInt(1, it.sekvensnummer)
                    prepStmt2.setString(2, stans.startPeriode)
                    prepStmt2.setString(3, stans.sluttPeriode)
                    prepStmt2.addBatch()
                }
            }
        }
        prepStmt1.executeBatch()
        prepStmt2.executeBatch()
        commit()
    }

    fun Connection.saveAllGeneratedTrekk(trekkTableList: List<TrekkTable>) {
        val prepStmt =
            prepareStatement(
                """
                insert into generertetrekk (
                sekvensnr,
                sekvensnr_nav,
                trekkid_ske, 
                trekkversjon, 
                trekkopprettet, 
                trekkpliktig, 
                skyldner, 
                trekkstatus, 
                startperiode, 
                sluttperiode, 
                trekkbelop, 
                trekkprosent, 
                corrid,
                status,
                kid, 
                kontonummer 
                ) values (?,?,?,?,?,?,?,?,?,?,?,?,?,'MOTTATT',?,?)
                """.trimIndent(),
            )
        trekkTableList.forEach {
            val trekkBelop = it.trekkbelop
            val trekkProsent = it.trekkprosent
            val sekvensnrNav = it.corrid.substring(it.corrid.length - 4).substringAfterLast("-", "0")
            prepStmt.setInt(1, it.sekvensnr)
            prepStmt.setInt(2, sekvensnrNav.toInt())
            prepStmt.setString(3, it.trekkid)
            prepStmt.setInt(4, it.trekkversjon)
            prepStmt.setString(5, it.tidspunktOpprettet.toString())
            prepStmt.setString(6, it.trekkpliktig)
            prepStmt.setString(7, it.skyldner)
            prepStmt.setString(8, it.trekkstatus)
            prepStmt.setString(9, it.startPeriode)
            prepStmt.setString(10, it.sluttPeriode)
            prepStmt.setDouble(11, trekkBelop ?: 0.0)
            prepStmt.setDouble(12, trekkProsent ?: 0.0)
            prepStmt.setString(13, UUID.randomUUID().toString())
            prepStmt.setString(14, it.kid)
            prepStmt.setString(15, it.kontonummer)
            prepStmt.addBatch()
        }
        prepStmt.executeBatch()
        commit()
    }

    fun Connection.fetchAllTrekkNotSent(): List<TrekkTable> =
        prepareStatement("""select * from trekk where status     = 'MOTTATT'""")
            .executeQuery()
            .toTrekkTable()

    fun Connection.fetcMidletidigStansForSekvensnr(sekvensnr: Int): List<MidlertidigStansTable> =
        prepareStatement(
            """
            select * from midlertidigstans where trekksekvensnr = ?
            """.trimIndent(),
        ).withParameters(
            param(sekvensnr),
        ).executeQuery()
            .toMidlertidigStans()
}