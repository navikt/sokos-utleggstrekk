package no.nav.sokos.utleggstrekk.database

import no.nav.sokos.utleggstrekk.database.model.TrekkFraSkatt

object TestRepository {
    fun Repository.getTrekkFraSkatt(id: Long): TrekkFraSkatt =
        withTransaction { session ->
            getTrekkFraSkatt(id, session)
        }
}