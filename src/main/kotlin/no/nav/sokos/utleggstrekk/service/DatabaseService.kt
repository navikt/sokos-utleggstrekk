package no.nav.sokos.utleggstrekk.service

import com.zaxxer.hikari.HikariDataSource
import mu.KotlinLogging
import no.nav.sokos.utleggstrekk.database.PostgresDataSource
import no.nav.sokos.utleggstrekk.database.Repository.doesTrekkExist
import no.nav.sokos.utleggstrekk.database.Repository.fetchAllPerioderForTrekk
import no.nav.sokos.utleggstrekk.database.Repository.fetchLastSekvensnr
import no.nav.sokos.utleggstrekk.database.Repository.fetchPerioderForTrekkVersion
import no.nav.sokos.utleggstrekk.database.Repository.fetchTrekkNotSendt
import no.nav.sokos.utleggstrekk.database.Repository.saveAllNewUtleggstrekk
import no.nav.sokos.utleggstrekk.database.Repository.saveFeilkoder
import no.nav.sokos.utleggstrekk.database.Repository.savePerioder
import no.nav.sokos.utleggstrekk.database.Repository.updateTrekkStatus
import no.nav.sokos.utleggstrekk.database.Repository.updateTrekkStatusSentAndDateTimeSentOS
import no.nav.sokos.utleggstrekk.database.RepositoryExtensions.useAndHandleErrors
import no.nav.sokos.utleggstrekk.database.model.TrekkPeriodeTable
import no.nav.sokos.utleggstrekk.database.model.UtleggstrekkTable
import no.nav.sokos.utleggstrekk.domene.nav.TrekkTilOppdrag
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
            if (status == SENDT) {
                con.updateTrekkStatusSentAndDateTimeSentOS(corrId)
            } else {
                con.updateTrekkStatus(corrId, status)
            }
        }
    }

    fun hentAlleTrekkSomIkkeErSendt(): List<UtleggstrekkTable> =
        dataSource.connection.useAndHandleErrors { con ->
            con.fetchTrekkNotSendt()
        }

    fun hentAllePerioderForTrekkId(trekk: UtleggstrekkTable): List<TrekkPeriodeTable> =
        dataSource.connection.useAndHandleErrors { con ->
            con.fetchAllPerioderForTrekk(trekk)
        }

    fun hentAllePerioderForTrekkVersjon(trekk:UtleggstrekkTable):List<TrekkPeriodeTable> =
        dataSource.connection.useAndHandleErrors { con ->
            con.fetchPerioderForTrekkVersion(trekk)
        }

    fun hentSisteSekvensnummer(): Int =
        dataSource.connection.useAndHandleErrors { con ->
            con.fetchLastSekvensnr()
        }

    fun lagreUtleggstrekk(trekkListe: List<Trekkpaalegg>) {
        dataSource.connection.useAndHandleErrors { con ->
            con.saveAllNewUtleggstrekk(trekkListe)
        }
    }

    fun lagreGenerertePerioder(perioder: List<TrekkPeriodeTable>){
        dataSource.connection.useAndHandleErrors { con ->
            con.savePerioder(perioder)
        }
    }

    fun lagreFeilkoderFraOS(kvitteringer: List<TrekkTilOppdrag>){
        val kvitteringerMedFeilkoder = kvitteringer.filter { it.mmel!!.alvorlighetsgrad != "00" }
        if (kvitteringerMedFeilkoder.isNotEmpty()) {
            dataSource.connection.useAndHandleErrors { con ->
                con.saveFeilkoder(kvitteringerMedFeilkoder)
            }
        }

}}