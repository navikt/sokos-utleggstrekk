package no.nav.sokos.utleggstrekk.service

import com.zaxxer.hikari.HikariDataSource
import mu.KotlinLogging
import no.nav.sokos.utleggstrekk.database.PostgresDataSource
import no.nav.sokos.utleggstrekk.database.Repository.getLastSekvensnr
import no.nav.sokos.utleggstrekk.database.RepositoryExtensions.useAndHandleErrors

private val logger = KotlinLogging.logger { }

class DatabaseService(
    private val dataSource: HikariDataSource = PostgresDataSource.dataSource(),
) {
    fun hentSisteSekvensnummer(): Int =
        dataSource.connection.useAndHandleErrors { con ->
            con.getLastSekvensnr()
        }
}