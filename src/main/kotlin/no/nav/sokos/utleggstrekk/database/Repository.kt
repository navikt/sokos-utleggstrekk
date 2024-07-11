package no.nav.sokos.utleggstrekk.database

import mu.KotlinLogging
import no.nav.sokos.utleggstrekk.database.RepositoryExtensions.getColumn
import no.nav.sokos.utleggstrekk.database.RepositoryExtensions.param
import no.nav.sokos.utleggstrekk.database.RepositoryExtensions.withParameters
import no.nav.sokos.utleggstrekk.domene.ske.Utleggstrekk
import java.sql.Connection

private val logger = KotlinLogging.logger { }

object Repository {
    fun Connection.getLastSekvensnr(): Int {
        val rs = prepareStatement("""select max(sekvensnr) from utleggstrekk""").executeQuery()
        return if (rs.next()) {
            rs.getColumn("sekvensnr")
        } else {
            0
        }
    }

    fun Connection.sjekkOmTrekkfinnes(sekvensnr: Int): Boolean =
        prepareStatement(
        """
           select 1 from utleggstrekk where sekvensnr = ? 
        """.trimIndent()
        ).withParameters(
            param(sekvensnr)
        ).executeQuery().next()

    fun Connection.saveAllNewUtleggstrekk(
        trekkListe: List<Utleggstrekk>,
    ) {
        val prepStmt =
            prepareStatement(
                """
                insert into utleggstrekk (
                sekvensnr,
                trekkid_ske, 
                trekkversjon, 
                trekkopprettet, 
                trekkpliktig, 
                skyldner, 
                trekkstatus, 
                startperiode, 
                sluttperiode, 
                trekkbeloep, 
                trekkprosent, 
                kidnummer, 
                kontonummer 
                ) values (?,?,?,TO_TIMESTAMP(?, 'YYYY-MM-DD"T"HH24:MI:SS.MSZ'),?,?,?,?,?,?,?,?,?)
                """.trimIndent(),
            )
        trekkListe.forEach {
            val trekkBelop = it.trekkbeloep?.trekkbeloep
            val trekkProsent = it.trekkprosent?.trekkprosent
            prepStmt.setInt(1, it.sekvensnummer)
            prepStmt.setString(2, it.trekkid)
            prepStmt.setInt(3, it.trekkversjon)
            prepStmt.setString(4, it.opprettet)
            prepStmt.setString(5, it.trekkpliktig)
            prepStmt.setString(6, it.skyldner)
            prepStmt.setString(7, it.trekkstatus)
            prepStmt.setString(8, it.startPeriode)
            prepStmt.setString(9, it.sluttPeriode)
            prepStmt.setDouble(10, trekkBelop ?: 0.0)
            prepStmt.setDouble(11, trekkProsent ?: 0.0)
            prepStmt.setString(12, it.kidnummer)
            prepStmt.setString(13, it.kontonummer)
            prepStmt.addBatch()
        }
        prepStmt.executeBatch()
        commit()
    }
}