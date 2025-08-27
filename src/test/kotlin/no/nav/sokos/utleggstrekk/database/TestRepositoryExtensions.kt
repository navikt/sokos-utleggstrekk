package no.nav.sokos.utleggstrekk.database

import kotliquery.Session
import kotliquery.queryOf
import no.nav.sokos.utleggstrekk.database.model.UtleggstrekkTable

object TestRepositoryExtensions {
    fun Repository.clearDb(session: Session) {
        session.update(queryOf("DELETE FROM trekkperiode"))
        session.update(queryOf("DELETE FROM utleggstrekk"))
        session.update(queryOf("DELETE FROM feilkoder"))
        session.update(queryOf("ALTER SEQUENCE feilkoder_id_seq RESTART WITH 1"))
        session.update(queryOf("ALTER SEQUENCE utleggstrekk_id_seq RESTART WITH 1"))
        session.update(queryOf("ALTER SEQUENCE trekkperiode_id_seq RESTART WITH 1"))
    }

    fun Repository.fetchAllUtleggstrekk(session: Session) =
        session.list(queryOf("SELECT * FROM utleggstrekk")) { row-> UtleggstrekkTable(row) }
}