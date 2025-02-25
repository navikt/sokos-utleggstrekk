package no.nav.sokos.utleggstrekk.database

import mu.KotlinLogging
import no.nav.sokos.utleggstrekk.database.RepositoryExtensions.getColumn
import no.nav.sokos.utleggstrekk.database.RepositoryExtensions.param
import no.nav.sokos.utleggstrekk.database.RepositoryExtensions.toTrekkpaleggTable
import no.nav.sokos.utleggstrekk.database.RepositoryExtensions.toTrekkpaleggperiodeTable
import no.nav.sokos.utleggstrekk.database.RepositoryExtensions.withParameters
import no.nav.sokos.utleggstrekk.database.model.TrekkPeriodeTable
import no.nav.sokos.utleggstrekk.database.model.UtleggstrekkTable
import no.nav.sokos.utleggstrekk.domene.nav.TrekkAlternativ
import no.nav.sokos.utleggstrekk.domene.ske.Trekkpaalegg
import java.sql.Connection
import java.sql.Timestamp
import java.util.UUID

private val logger = KotlinLogging.logger { }

object Repository {
    fun Connection.fetchLastSekvensnr(): Int {
        val rs = prepareStatement("""select max(sekvensnummer) from utleggstrekk""").executeQuery()
        return if (rs.next()) {
            rs.getColumn("sekvensnummer")
        } else {
            0
        }
    }

    fun Connection.doesTrekkExist(trekkid_ske: String, sekvensnummer: Int, trekkversjon: Int): Boolean =
        prepareStatement(
            """
            select 1 from utleggstrekk where sekvensnummer = ? and trekkid_ske = ? and trekkversjon = ?
            """.trimIndent(),
        ).withParameters(
            param(sekvensnummer),
            param(trekkid_ske),
            param(trekkversjon),
        ).executeQuery()
            .next()

    fun Connection.updateTrekkStatus(corrId: String, status: String) {
        prepareStatement(
            """
                update utleggstrekk set status = ? 
                where corrid = ?;
                """.trimIndent(),
        ).withParameters(
            param(status),
            param(corrId),
        ).executeUpdate()
        commit()
    }

    fun Connection.updateKvitteringStatus(corrId: String, status: String, kvittering: String, navTrekkId: String) {
        prepareStatement(
            """
                update utleggstrekk set status = ?, kvittering = ?, trekkid_nav = ?  
                where corrid = ?;
                """.trimIndent(),
        ).withParameters(
            param(status),
            param(kvittering),
            param(navTrekkId),
            param(corrId)
        ).executeUpdate()
        commit()
    }

    fun Connection.saveAllNewUtleggstrekk(
        trekkListe: List<Trekkpaalegg>,
    ) {
        val prepStmt1 =
            prepareStatement(
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
                sats,
                trekkalternativ
                ) values (?,?,?,?,?,?,?)        
                """.trimIndent(),
            )
        trekkListe.forEach { trekk ->
            prepStmt1.setInt(1, trekk.sekvensnummer)
            prepStmt1.setString(2, trekk.trekkid)
            prepStmt1.setInt(3, trekk.trekkversjon)
            prepStmt1.setString(4, trekk.saksnummer)
            prepStmt1.setTimestamp(5, Timestamp(trekk.opprettet.toEpochMilliseconds()))
            prepStmt1.setString(6, trekk.trekkpliktig)
            prepStmt1.setString(7, trekk.skyldner)
            prepStmt1.setString(8, trekk.trekkstatus)
            prepStmt1.setString(9, trekk.betalingsinformasjon.betalingsmottaker)
            prepStmt1.setString(10, trekk.betalingsinformasjon.kidnummer)
            prepStmt1.setString(11, trekk.betalingsinformasjon.kontonummer)
            prepStmt1.setString(12, UUID.randomUUID().toString())
            prepStmt1.addBatch()
            trekk.trekkstoerrelseForPeriode.forEach { periode ->
                val trekkalternativ = TrekkAlternativ.getTrekkAlternativ(periode).value
                prepStmt2.setInt(1, trekk.sekvensnummer)
                prepStmt2.setString(2, trekk.trekkid)
                prepStmt2.setInt(3, trekk.trekkversjon)
                prepStmt2.setString(4, periode.startdato)
                prepStmt2.setString(5, periode.sluttdato)
                prepStmt2.setObject(6, periode.trekkbeloep?.trekkbeloep ?: periode.trekkprosent?.trekkprosent, java.sql.Types.DOUBLE)
                prepStmt2.setString(7, trekkalternativ)
                prepStmt2.addBatch()
            }
        }
        prepStmt1.executeBatch()
        prepStmt2.executeBatch()
        commit()
    }

    fun Connection.updateWithTrekkAlternativ(trekk: UtleggstrekkTable) {
        if (trekk.trekkidSkeOS == null || trekk.trekkAlternativ == null)
            throw RuntimeException("TrekkidSkeOs/Trekkalternativ må være satt for for at de skal oppdateres (trekkid: ${trekk.trekkidSke}, versjon: ${trekk.trekkversjon} ${trekk.utleggstrekkTableId}")
        prepareStatement(
            """
            update utleggstrekk
            set trekkid_ske_os = ?, trekkalternativ = ?
            where id = ?
        """.trimIndent()
        )
            .withParameters(
                param(trekk.trekkidSkeOS),
                param(trekk.trekkAlternativ),
                param(trekk.utleggstrekkTableId)
            )
            .executeUpdate()
        commit()
    }

    fun Connection.insertGeneratedTrekkpalegg(trekk: UtleggstrekkTable) {
        prepareStatement(
            """
                insert into utleggstrekk (
                sekvensnummer,
                trekkid_ske, 
                trekkid_ske_os,
                trekkversjon, 
                saksnummer,
                opprettet_ske, 
                trekkpliktig, 
                skyldner, 
                trekkstatus,
                trekkalternativ,
                betalingsmottaker,
                kid, 
                kontonummer, 
                corrid,
                status
                ) values (?,?,?, ?,?,?,?,?,?,?,?,?,?,?,'MOTTATT')
                """.trimIndent()
        )
            .withParameters(
                param(trekk.sekvensnummer),
                param(trekk.trekkidSke),
                param(trekk.trekkidSkeOS!!),
                param(trekk.trekkversjon),
                param(trekk.saksnummer),
                param(trekk.opprettetSke),
                param(trekk.trekkpliktig),
                param(trekk.skyldner),
                param(trekk.trekkstatus),
                param(trekk.trekkAlternativ!!),
                param(trekk.betalingsmottaker),
                param(trekk.kid),
                param(trekk.kontonummer),
                param(UUID.randomUUID().toString()),
            )
            .executeUpdate()
        commit()

    }

    fun Connection.fetchAllTrekkNotSent(): List<UtleggstrekkTable> =
        prepareStatement("""select * from utleggstrekk where status     = 'MOTTATT'""")
            .executeQuery()
            .toTrekkpaleggTable()

    fun Connection.fetchAllTrekkWithoutTrekkAlternativ(): List<UtleggstrekkTable> =
        prepareStatement("""select * from utleggstrekk where trekkalternativ is null or trekkalternativ = ''""")
            .executeQuery()
            .toTrekkpaleggTable()


    fun Connection.fetchAllPerioderForTrekkVersion(trekk: UtleggstrekkTable): List<TrekkPeriodeTable> =
        prepareStatement(
            """
            select * from trekkperiode 
            where sekvensnummer = ?
                and trekkid_ske = ?
                and trekkversjon= ?

        """.trimIndent()
        )
            .withParameters(
                param(trekk.sekvensnummer),
                param(trekk.trekkidSke),
                param(trekk.trekkversjon)
            )
            .executeQuery()
            .toTrekkpaleggperiodeTable()

    fun Connection.fetchPerioderForTrekkWithTrekkAlternativ(trekk: UtleggstrekkTable): List<TrekkPeriodeTable> {

        if (trekk.trekkAlternativ == null) {
            throw RuntimeException("Kan ikke søke opp perioder med trekkalternativ for Trekk som ikke har satt trekkalternativ(trekkidske: ${trekk.trekkidSke} trekkversjon: ${trekk.trekkversjon})")
        }

        return prepareStatement(
            """
            select * from trekkperiode 
            where sekvensnummer = ?
                and trekkid_ske = ?
                and trekkversjon = ?
                and trekkalternativ = ?

        """.trimIndent()
        )
            .withParameters(
                param(trekk.sekvensnummer),
                param(trekk.trekkidSke),
                param(trekk.trekkversjon),
                param(trekk.trekkAlternativ)
            )
            .executeQuery()
            .toTrekkpaleggperiodeTable()
    }

}