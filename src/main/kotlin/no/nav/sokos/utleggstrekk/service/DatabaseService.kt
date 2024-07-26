package no.nav.sokos.utleggstrekk.service

import com.zaxxer.hikari.HikariDataSource
import mu.KotlinLogging
import no.nav.sokos.utleggstrekk.database.PostgresDataSource
import no.nav.sokos.utleggstrekk.database.Repository.checkIfTrekkfinnes
import no.nav.sokos.utleggstrekk.database.Repository.fetcMidletidigStansForSekvensnr
import no.nav.sokos.utleggstrekk.database.Repository.fetchAllTrekkNotSent
import no.nav.sokos.utleggstrekk.database.Repository.fetchLastSekvensnr
import no.nav.sokos.utleggstrekk.database.Repository.saveAllGeneratedTrekk
import no.nav.sokos.utleggstrekk.database.Repository.saveAllNewUtleggstrekk
import no.nav.sokos.utleggstrekk.database.RepositoryExtensions.useAndHandleErrors
import no.nav.sokos.utleggstrekk.database.model.MidlertidigStansTable
import no.nav.sokos.utleggstrekk.database.model.TrekkTable
import no.nav.sokos.utleggstrekk.domene.ske.Utleggstrekk

private val logger = KotlinLogging.logger { }

class DatabaseService(
    private val dataSource: HikariDataSource = PostgresDataSource.dataSource(),
) {
    fun hentSisteSekvensnummer(): Int =
        dataSource.connection.useAndHandleErrors { con ->
            con.fetchLastSekvensnr().also { con.close() }
        }

    fun trekkFinnes(sekvensnr: Int): Boolean =
        dataSource.connection.useAndHandleErrors { con ->
            con.checkIfTrekkfinnes(sekvensnr).also { con.close() }
        }

    fun lagreAlleNyeUtleggstrekk(trekkListe: List<Utleggstrekk>) {
        dataSource.connection.useAndHandleErrors { con ->
            con.saveAllNewUtleggstrekk(trekkListe).also { con.close() }
        }
    }

    fun lagreAlleGenererteTrekk(trekkTableList: List<TrekkTable>) {
        dataSource.connection.useAndHandleErrors { con ->
            con.saveAllGeneratedTrekk(trekkTableList).also { con.close() }
        }
    }

    fun hentAlleTrekkSomIkkeErSendt(): List<TrekkTable> =
        dataSource.connection.useAndHandleErrors { con ->
            con.fetchAllTrekkNotSent().also { con.close() }
    }

    fun hentMidletidigStansForSekvensnr(sekvensnr: Int): List<MidlertidigStansTable> =
        dataSource.connection.useAndHandleErrors { con ->
            con.fetcMidletidigStansForSekvensnr(sekvensnr)
        }
}