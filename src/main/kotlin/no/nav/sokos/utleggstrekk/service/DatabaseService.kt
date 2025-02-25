package no.nav.sokos.utleggstrekk.service

import com.zaxxer.hikari.HikariDataSource
import mu.KotlinLogging
import no.nav.sokos.utleggstrekk.database.PostgresDataSource
import no.nav.sokos.utleggstrekk.database.Repository.doesTrekkExist
import no.nav.sokos.utleggstrekk.database.Repository.fetchAllPerioderForTrekkVersion
import no.nav.sokos.utleggstrekk.database.Repository.fetchAllTrekkNotSent
import no.nav.sokos.utleggstrekk.database.Repository.fetchAllTrekkWithoutTrekkAlternativ
import no.nav.sokos.utleggstrekk.database.Repository.fetchLastSekvensnr
import no.nav.sokos.utleggstrekk.database.Repository.fetchPerioderForTrekkWithTrekkAlternativ
import no.nav.sokos.utleggstrekk.database.Repository.insertGeneratedTrekkpalegg
import no.nav.sokos.utleggstrekk.database.Repository.saveAllNewUtleggstrekk
import no.nav.sokos.utleggstrekk.database.Repository.updateTrekkStatus
import no.nav.sokos.utleggstrekk.database.Repository.updateWithTrekkAlternativ
import no.nav.sokos.utleggstrekk.database.RepositoryExtensions.useAndHandleErrors
import no.nav.sokos.utleggstrekk.database.model.TrekkPeriodeTable
import no.nav.sokos.utleggstrekk.database.model.UtleggstrekkTable
import no.nav.sokos.utleggstrekk.domene.ske.Trekkpaalegg

private val logger = KotlinLogging.logger { }

class DatabaseService(
    private val dataSource: HikariDataSource = PostgresDataSource.dataSource,
) {
    fun trekkFinnes(trekkid_ske: String, sekvensnr: Int, trekkversjon: Int) =
        dataSource.connection.useAndHandleErrors { con ->
            con.doesTrekkExist(trekkid_ske, sekvensnr, trekkversjon)
        }

    fun oppdaterTrekkStatus(corrId: String, status: String) {
        dataSource.connection.useAndHandleErrors { con ->
            con.updateTrekkStatus(corrId, status)
        }
    }

    fun oppdaterTrekkMedTrekkAlternativ(trekkListe: List<UtleggstrekkTable>) {
        dataSource.connection.useAndHandleErrors { con ->
            con.updateWithTrekkAlternativ(trekkListe[0])
            if (trekkListe.size == 2){
                con.insertGeneratedTrekkpalegg(trekkListe[1])
            }
        }
    }

    fun hentAlleTrekkSomIkkeErSendt(): List<UtleggstrekkTable> =
        dataSource.connection.useAndHandleErrors { con ->
            con.fetchAllTrekkNotSent()
        }

    fun hentAlleTrekkutenTrekkAlternativ(): List<UtleggstrekkTable> =
        dataSource.connection.useAndHandleErrors { con ->
            con.fetchAllTrekkWithoutTrekkAlternativ()
        }

    fun hentAllePerioderForTrekkVersjon(trekk:UtleggstrekkTable):List<TrekkPeriodeTable> =
        dataSource.connection.useAndHandleErrors { con ->
            con.fetchAllPerioderForTrekkVersion(trekk)
        }

    fun hentPerioderForTrekkVersjonOgTrekkAlternativ(trekk:UtleggstrekkTable):List<TrekkPeriodeTable> =
        dataSource.connection.useAndHandleErrors { con ->
            con.fetchPerioderForTrekkWithTrekkAlternativ(trekk)
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