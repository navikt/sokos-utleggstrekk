package no.nav.sokos.utleggstrekk.database

import kotliquery.Session
import kotliquery.queryOf

import no.nav.sokos.utleggstrekk.database.model.UtleggstrekkTable

object TestRepositoryExtensions {
    fun Repository.clearDb(session: Session) {
        session.update(queryOf("DELETE FROM fraskatt"))
        session.update(queryOf("DELETE FROM betalingsinformasjonfraskatt"))
        session.update(queryOf("DELETE FROM periode"))
        session.update(queryOf("DELETE FROM feilkoder"))
        session.update(queryOf("ALTER SEQUENCE feilkoder_id_seq RESTART WITH 1"))
        session.update(queryOf("ALTER SEQUENCE fraskatt_id_seq RESTART WITH 1"))
        session.update(queryOf("ALTER SEQUENCE periode_id_seq RESTART WITH 1"))
        session.update(queryOf("ALTER SEQUENCE betalingsinformasjonfraskatt_id_seq RESTART WITH 1"))
    }

    fun Repository.fetchAllUtleggstrekk(session: Session) = session.list(queryOf("SELECT * FROM utleggstrekk")) { row -> UtleggstrekkTable(row) }
}
