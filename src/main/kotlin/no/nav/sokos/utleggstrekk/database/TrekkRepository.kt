package no.nav.sokos.utleggstrekk.database

import com.zaxxer.hikari.HikariDataSource
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.sokos.utleggstrekk.database.model.FeilkodeTable
import no.nav.sokos.utleggstrekk.database.model.UtleggstrekkTable
import no.nav.sokos.utleggstrekk.utils.SQLUtils.withTransaction

class TrekkRepository(
    private val dataSource: HikariDataSource
) {

    fun clearDb() {
        dataSource.withTransaction { session ->
            session.update(queryOf("DELETE FROM trekkperiode"))
            session.update(queryOf("DELETE FROM utleggstrekk"))
            session.update(queryOf("ALTER SEQUENCE utleggstrekk_id_seq RESTART WITH 1"))
            session.update(queryOf("ALTER SEQUENCE trekkperiode_id_seq RESTART WITH 1"))
        }
    }

    fun findFeilkode(corrId: String): FeilkodeTable? =
        using(sessionOf(dataSource)) { session ->
            session.single(
                queryOf("SELECT * FROM feilkoder WHERE corr_id=:corrId", mapOf("corrId" to corrId))
            ) { row -> FeilkodeTable(row) }
        }

    fun findTrekkByCorrId(corrId: String): UtleggstrekkTable? =
        using(sessionOf(dataSource)) { session ->
            session.single(
                queryOf("SELECT * FROM utleggstrekk WHERE corr_id=:corrId", mapOf("corrId" to corrId))
            ) { row -> UtleggstrekkTable(row) }
        }
}