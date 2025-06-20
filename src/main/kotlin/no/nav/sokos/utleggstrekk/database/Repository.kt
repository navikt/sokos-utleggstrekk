package no.nav.sokos.utleggstrekk.database

import mu.KotlinLogging
import no.nav.sokos.utleggstrekk.database.RepositoryExtensions.param
import no.nav.sokos.utleggstrekk.database.RepositoryExtensions.toTrekkPeriodeTable
import no.nav.sokos.utleggstrekk.database.RepositoryExtensions.toUtleggstrekkTable
import no.nav.sokos.utleggstrekk.database.RepositoryExtensions.withParameters
import no.nav.sokos.utleggstrekk.database.model.TrekkPeriodeTable
import no.nav.sokos.utleggstrekk.database.model.UtleggstrekkTable
import no.nav.sokos.utleggstrekk.domene.nav.TrekkAlternativ
import no.nav.sokos.utleggstrekk.domene.nav.TrekkTilOppdrag
import no.nav.sokos.utleggstrekk.domene.ske.Trekkpaalegg
import no.nav.sokos.utleggstrekk.service.SENDT
import java.sql.Connection
import java.sql.Timestamp
import java.util.UUID

private val logger = KotlinLogging.logger { }
private const val MOTTATT = "MOTTATT"
private const val SKATTEETATEN = "SKATTEETATEN"
private const val MAX_SLUTTDATO = "9999-12-31"

object Repository {
    fun Connection.fetchLastSekvensnr(): Int {
        val rs = prepareStatement("""select max(sekvensnummer)  from utleggstrekk""").executeQuery()
        return if (rs.next()) {
            rs.getInt(1)
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

    fun Connection.updateNavTrekkStatus(corrId: String, status: String) {
        prepareStatement(
            """
                update utleggstrekk set status = ?, tidspunkt_siste_status = now() 
                where corr_id = ?;
                """.trimIndent(),
        ).withParameters(
            param(status),
            param(corrId),
        ).executeUpdate()
        commit()
    }

    fun Connection.updateTrekkStatusSentAndDateTimeSentOS(corrId: String) {
        prepareStatement(
            """
                update utleggstrekk set status = ?, 
                                        tidspunkt_siste_status = now(),
                                        tidspunkt_sendt_os = now()
                where corr_id = ?
            """.trimIndent()
        ).withParameters(
            param(SENDT),
            param(corrId)
        ).executeUpdate()
        commit()
    }


    fun Connection.updateKvitteringStatus(corrId: String, status: String, kvittering: String, navTrekkId: String, trekkalternativ: String) {
        val kvitteringAlternativ = when (trekkalternativ){
            "LOPM" -> "kvitteringLOPM"
            else -> "kvitteringLOPP"
        }
        prepareStatement(
            """
                update utleggstrekk set status = ?, $kvitteringAlternativ = ?, trekkid_nav = ?, tidspunkt_siste_status = NOW()  
                where corr_id = ?;
            """.trimIndent()
        ).withParameters(
            param(status),
            param(kvittering),
            param(navTrekkId),
            param(corrId)
        ).executeUpdate()
        commit()
    }

    fun Connection.savePerioder(
        perioder: List<TrekkPeriodeTable>
    ){
        val prepStmt =
            prepareStatement(
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
            )
        perioder.forEach { periode ->
            prepStmt.setInt(1, periode.sekvensnummer)
            prepStmt.setString(2, periode.trekkidSke)
            prepStmt.setInt(3, periode.trekkversjon)
            prepStmt.setString(4, periode.datoStart)
            prepStmt.setString(5, periode.datoSlutt)
            prepStmt.setObject(6, periode.sats, java.sql.Types.DOUBLE)
            prepStmt.setString(7, periode.trekkAlternativ)
            prepStmt.setString(8, periode.kilde)
            prepStmt.addBatch()
        }

        prepStmt.executeBatch()
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
                corr_id,
                status
                ) values (?,?,?,?,?,?,?,?,?,?,?,?,?)
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
                trekkalternativ,
                kilde
                ) values (?,?,?,?,?,?,?,?)        
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
            prepStmt1.setString(13, MOTTATT)
            prepStmt1.addBatch()
            trekk.trekkstoerrelseForPeriode.forEach { periode ->
                val trekkalternativ = TrekkAlternativ.getTrekkAlternativ(periode).value
                val sluttdato = periode.sluttdato ?: MAX_SLUTTDATO
                prepStmt2.setInt(1, trekk.sekvensnummer)
                prepStmt2.setString(2, trekk.trekkid)
                prepStmt2.setInt(3, trekk.trekkversjon)
                prepStmt2.setString(4, periode.startdato)
                prepStmt2.setString(5, sluttdato)
                prepStmt2.setObject(6, periode.trekkbeloep?.trekkbeloep ?: periode.trekkprosent?.trekkprosent, java.sql.Types.DOUBLE)
                prepStmt2.setString(7, trekkalternativ)
                prepStmt2.setString(8, SKATTEETATEN)
                prepStmt2.addBatch()
            }
        }
        prepStmt1.executeBatch()
        prepStmt2.executeBatch()
        commit()
    }

    fun Connection.fetchTrekkNotSendt(): List<UtleggstrekkTable> =
        prepareStatement(
            """
            select * from utleggstrekk where status = ?
            """.trimIndent())
            .withParameters(
                param(MOTTATT)
            )
            .executeQuery()
            .toUtleggstrekkTable()

    fun Connection.fetchPerioderForTrekkVersion(trekk: UtleggstrekkTable): List<TrekkPeriodeTable> =
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
            .toTrekkPeriodeTable()

    fun Connection.fetchAllPerioderForTrekk(trekk: UtleggstrekkTable): List<TrekkPeriodeTable> {

        return prepareStatement(
            """
            select * from trekkperiode 
            where sekvensnummer = ?
                and trekkid_ske = ?
                and trekkversjon = ?

        """.trimIndent()
        )
            .withParameters(
                param(trekk.sekvensnummer),
                param(trekk.trekkidSke),
                param(trekk.trekkversjon),
            )
            .executeQuery()
            .toTrekkPeriodeTable()
    }


    fun Connection.saveFeilkoder(kvitteringer: List<TrekkTilOppdrag>) {
        kvitteringer.forEach { kvittering ->
            prepareStatement(
                """
                insert into feilkoder (
                kreditor_trekk_id ,
                corr_id,
                trekkalternativ,
                feilkode,
                beskrivelse
                ) values (?,?,?,?,?)        
            """.trimIndent()
            )
                .withParameters(
                    param(kvittering.dokument.innrapporteringTrekk.kreditorTrekkId),
                    param(kvittering.dokument.transaksjonsId),
                    param(kvittering.dokument.innrapporteringTrekk.kodeTrekkAlternativ),
                    param(kvittering.mmel?.kodeMelding ?: "INGEN KODE MOTTATT FRA OS"),
                    param(kvittering.mmel?.beskrMelding ?: "INGEN BESKRIVELSE MOTTATT FRA OS")
                )
        }
    }
}