package no.nav.sokos.utleggstrekk.service

import com.zaxxer.hikari.HikariDataSource
import mu.KotlinLogging
import no.nav.sokos.utleggstrekk.database.PostgresDataSource
import no.nav.sokos.utleggstrekk.database.Repository.getLastSekvensnr
import no.nav.sokos.utleggstrekk.database.Repository.saveAllNewUtleggstrekk
import no.nav.sokos.utleggstrekk.database.Repository.sjekkOmTrekkfinnes
import no.nav.sokos.utleggstrekk.database.RepositoryExtensions.useAndHandleErrors
import no.nav.sokos.utleggstrekk.domene.ske.Utleggstrekk

private val logger = KotlinLogging.logger { }

class DatabaseService(
    private val dataSource: HikariDataSource = PostgresDataSource.dataSource(),
) {
    fun hentSisteSekvensnummer(): Int =
        dataSource.connection.useAndHandleErrors { con ->
            con.getLastSekvensnr().also { con.close() }
        }

    fun trekkFinnes(sekvensnr: Int): Boolean =
        dataSource.connection.useAndHandleErrors { con ->
            con.sjekkOmTrekkfinnes(sekvensnr).also { con.close() }
        }

    fun lagreAlleNyeUtleggstrekk(trekkListe: List<Utleggstrekk>) {
        dataSource.connection.useAndHandleErrors { con ->
            con.saveAllNewUtleggstrekk(trekkListe).also { con.close() }
        }
    }
}