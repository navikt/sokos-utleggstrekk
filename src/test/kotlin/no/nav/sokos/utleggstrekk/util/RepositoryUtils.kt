package no.nav.sokos.utleggstrekk.util

import kotliquery.queryOf

import no.nav.sokos.utleggstrekk.database.Repository

fun Repository.insertRawSQL(sql: String) {
    withTransaction { session ->
        sql.split(";").forEachIndexed { index, statement ->
            try {
                session.update(
                    queryOf(statement),
                )
            } catch (e: Exception) {
                throw IllegalArgumentException("Error in $statement at line ${index + 1}, ${e.message}")
            }
        }
    }
}