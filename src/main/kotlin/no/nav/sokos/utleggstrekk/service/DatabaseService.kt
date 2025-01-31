package no.nav.sokos.utleggstrekk.service

import com.zaxxer.hikari.HikariDataSource
import mu.KotlinLogging
import no.nav.sokos.utleggstrekk.database.PostgresDataSource
import no.nav.sokos.utleggstrekk.database.Repository.doesTrekkExist
import no.nav.sokos.utleggstrekk.database.Repository.fetchAllTrekkNotSent
import no.nav.sokos.utleggstrekk.database.Repository.fetchLastSekvensnr
import no.nav.sokos.utleggstrekk.database.Repository.fetchPerioderForTrekk
import no.nav.sokos.utleggstrekk.database.Repository.saveAllNewUtleggstrekk
import no.nav.sokos.utleggstrekk.database.Repository.updateTrekkStatus
import no.nav.sokos.utleggstrekk.database.RepositoryExtensions.useAndHandleErrors
import no.nav.sokos.utleggstrekk.database.model.TrekkPeriodeTable
import no.nav.sokos.utleggstrekk.database.model.TrekkpaleggTable
import no.nav.sokos.utleggstrekk.domene.ske.Trekkpaalegg

private val logger = KotlinLogging.logger { }

class DatabaseService(
    private val dataSource: HikariDataSource = PostgresDataSource.dataSource(),
) {
    fun trekkFinnes(trekkid_ske: String, sekvensnr: Int, trekkversjon: Int) =
        dataSource.connection.useAndHandleErrors { con ->
            con.doesTrekkExist(trekkid_ske, sekvensnr, trekkversjon)
        }

    fun oppdaterTrekkStatus(trekk: TrekkpaleggTable, status: String) {
        dataSource.connection.useAndHandleErrors { con ->
            con.updateTrekkStatus(trekk, status)
        }
    }

    fun hentAlleTrekkSomIkkeErSendt(): List<TrekkpaleggTable> =
        dataSource.connection.useAndHandleErrors { con ->
            con.fetchAllTrekkNotSent()
        }

    fun hentPerioderForTrekk(trekk:TrekkpaleggTable):List<TrekkPeriodeTable> =
        dataSource.connection.useAndHandleErrors { con ->
            con.fetchPerioderForTrekk(trekk)
        }
    // brukes av testAPI
    fun hentSisteSekvensnummer(): Int =
        dataSource.connection.useAndHandleErrors { con ->
            con.fetchLastSekvensnr()
        }

    fun lagreUtleggstrekk(trekkListe: List<Trekkpaalegg>) {
        dataSource.connection.useAndHandleErrors { con ->
            con.saveAllNewUtleggstrekk(trekkListe)
        }
    }


}