package no.nav.sokos.utleggstrekk.database

import mu.KotlinLogging
import no.nav.sokos.utleggstrekk.database.RepositoryExtensions.getColumn
import no.nav.sokos.utleggstrekk.database.RepositoryExtensions.param
import no.nav.sokos.utleggstrekk.database.RepositoryExtensions.toTrekkPeriodeTable
import no.nav.sokos.utleggstrekk.database.RepositoryExtensions.toTrekkpalegTable
import no.nav.sokos.utleggstrekk.database.RepositoryExtensions.withParameters
import no.nav.sokos.utleggstrekk.database.model.TrekkPeriodeTable
import no.nav.sokos.utleggstrekk.database.model.TrekkpaleggTable
import no.nav.sokos.utleggstrekk.domene.ske.Trekkpaalegg
import java.sql.Connection
import java.util.UUID

private val logger = KotlinLogging.logger { }

object Repository {
    fun Connection.fetchLastSekvensnr(): Int {
        val rs = prepareStatement("""select max(sekvensnummer) from trekkpalegg""").executeQuery()
        return if (rs.next()) {
            rs.getColumn("sekvensnummer")
        } else {
            0
        }
    }

    fun Connection.doesTrekkExist(trekkid_ske: String, sekvensnummer: Int, trekkversjon: Int): Boolean =
        prepareStatement(
            """
            select 1 from trekkpalegg where sekvensnummer = ? and trekkid_ske = ? and trekkversjon = ?
            """.trimIndent(),
        ).withParameters(
            param(sekvensnummer),
            param(trekkid_ske),
            param(trekkversjon),
        ).executeQuery()
            .next()

    fun Connection.updateTrekkStatus(corrid: String, status: String) {
        println("oppdaterer status for corrid $corrid")
        val result =
            prepareStatement(
                """
                update trekkpalegg set status = ? where corrid = ?;
                """.trimIndent(),
            ).withParameters(
                param(status),
                param(corrid),
            ).executeUpdate()
        commit()
        println("oppdaterte $result rad")
    }

    fun Connection.saveAllNewUtleggstrekk(
        trekkListe: List<Trekkpaalegg>,
    ) {
        val prepStmt1 =
            prepareStatement(
                """
                insert into trekkpalegg (
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
                corrid,
                status
                ) values (?,?,?,?,?,?,?,?,?,?,?,?,'MOTTATT')
                """.trimIndent(),
            )
        val prepStmt2 =
            prepareStatement(
                """
                insert into trekkperiode (
                sekvensnummer,
                trekkid_ske,
                trekkversjon,
                dato_start, 
                dato_slutt,
                trekkbelop,
                trekkprosent
                ) values (?,?,?,?,?,?,?)        
                """.trimIndent(),
            )
        trekkListe.forEach { trekk ->
            prepStmt1.setInt(1, trekk.sekvensnummer)
            prepStmt1.setString(2, trekk.trekkid)
            prepStmt1.setInt(3, trekk.trekkversjon)
            prepStmt1.setString(4, trekk.saksnummer)
            prepStmt1.setString(5, trekk.opprettet)
            prepStmt1.setString(6, trekk.trekkpliktig)
            prepStmt1.setString(7, trekk.skyldner)
            prepStmt1.setString(8, trekk.trekkstatus)
            prepStmt1.setString(9, trekk.betalingsinformasjon.betalingsmottaker)
            prepStmt1.setString(10, trekk.betalingsinformasjon.kidnummer)
            prepStmt1.setString(11, trekk.betalingsinformasjon.kontonummer)
            prepStmt1.setString(12, UUID.randomUUID().toString())
            prepStmt1.addBatch()
            trekk.trekkstoerrelseForPeriode.forEach{ periode ->
                prepStmt2.setInt(1, trekk.sekvensnummer)
                prepStmt2.setString(2, trekk.trekkid)
                prepStmt2.setInt(3, trekk.trekkversjon)
                prepStmt2.setString(4, periode.startdato)
                prepStmt2.setString(5, periode.sluttdato)
                periode.trekkbeloep?.trekkbeloep?.let { prepStmt2.setDouble(6, it) }
                periode.trekkprosent?.trekkprosent?.let { prepStmt2.setDouble(7, it) }
                prepStmt2.addBatch()
            }
        }
        prepStmt1.executeBatch()
        prepStmt2.executeBatch()
        commit()
    }

    fun Connection.fetchAllTrekkNotSent(): List<TrekkpaleggTable> =
        prepareStatement("""select * from trekkpalegg where status     = 'MOTTATT'""")
            .executeQuery()
            .toTrekkpalegTable()


    fun Connection.fetchPerioderForTrekk(trekk: TrekkpaleggTable):List<TrekkPeriodeTable> =
        prepareStatement("""
            select * from trekkperiode 
            where sekvensnummer = ?
                and trekkid_ske = ?
                and trekkversjon= ?

        """.trimIndent())
            .withParameters(
                param(trekk.sekvensnummer),
                param(trekk.trekkidSke),
                param(trekk.trekkversjon)
            )
            .executeQuery()
            .toTrekkPeriodeTable()


}