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
import java.util.*

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

    fun Connection.checkIfTrekkfinnes(sekvensnr: Int): Boolean =
        prepareStatement(
        """
           select 1 from trekk where sekvensnr = ? 
        """.trimIndent()
        ).withParameters(
            param(sekvensnr)
        ).executeQuery().next()

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
            prepStmt1.setString(8, "${it.startPeriode}-1")
            prepStmt1.setString(9, periodeEndDay(it.sluttPeriode))
            prepStmt1.setDouble(10, trekkBelop ?: 0.0)
            prepStmt1.setDouble(11, trekkProsent ?: 0.0)
            prepStmt1.setString(12, UUID.randomUUID().toString())
            prepStmt1.setString(13, it.kid)
            prepStmt1.setString(14, it.kontonummer)
            prepStmt1.addBatch()

            if (!it.midlertidigStans.isNullOrEmpty()) {
                it.midlertidigStans.forEach { stans ->
                    prepStmt2.setInt(1, it.sekvensnummer)
                    prepStmt2.setString(2, stans.startPeriode ?: "ikke satt")
                    prepStmt2.setString(3, stans.sluttPeriode ?: "ikke satt")
                    prepStmt2.addBatch()
                }
            }
        }
        prepStmt1.executeBatch()
        prepStmt2.executeBatch()
        commit()
    }

    fun Connection.fetchAllTrekkNotSent(): List<TrekkTable> {
        return prepareStatement("""select * from trekk where status = 'MOTTATT'""")
            .executeQuery().toTrekkTable()

    }

    fun Connection.fetcMidletidigStansForSekvensnr(sekvensnr: Int): List<MidlertidigStansTable> {
        return prepareStatement(
            """
                select * from midlertidigstans where trekksekvensnr = ?
            """.trimIndent()
        ).withParameters(
            param(sekvensnr)
        ).executeQuery().toMidlertidigStans()

    }

}