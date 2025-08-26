package no.nav.sokos.utleggstrekk.service

import com.zaxxer.hikari.HikariDataSource
import mu.KotlinLogging
import no.nav.sokos.utleggstrekk.database.PostgresDataSource
import no.nav.sokos.utleggstrekk.database.Repository
import no.nav.sokos.utleggstrekk.database.model.TrekkPeriodeTable
import no.nav.sokos.utleggstrekk.database.model.UtleggstrekkTable
import no.nav.sokos.utleggstrekk.domene.nav.TrekkTilOppdrag
import no.nav.sokos.utleggstrekk.domene.ske.Trekkpaalegg
import no.nav.sokos.utleggstrekk.utils.SQLUtils.withTransaction

private val logger = KotlinLogging.logger { }

class DatabaseService(
    private val dataSource: HikariDataSource = PostgresDataSource.dataSource,
) {
    private val repository = Repository(dataSource)

    fun trekkFinnes(trekkid_ske: String, sekvensnr: Int, trekkversjon: Int) =
        dataSource.withTransaction { session ->
            repository.doesTrekkExist(trekkid_ske, sekvensnr, trekkversjon, session)
        }

    fun oppdaterTrekkStatus(corrId: String, status: String) {
        dataSource.withTransaction { session ->
            if (status == SENDT) {
                repository.updateTrekkStatusSentAndDateTimeSentOS(corrId, session)
            } else {
                repository.updateNavTrekkStatus(corrId, status, session)
            }
        }
    }

    fun oppdaterTrekkMedKvitteringsinfo(kvitteringer: List<TrekkTilOppdrag>) {
        kvitteringer.map { kvittering ->
            dataSource.withTransaction { session ->
                val status = when (kvittering.mmel?.alvorlighetsgrad) {
                    "00" -> "KVITTERING_OK"
                    else -> "KVITTERING_FEILET"
                }
                repository.updateKvitteringStatus(
                    kvittering.dokument.transaksjonsId,
                    status, kvittering.mmel?.kodeMelding ?: "Ingen kode i mmel",
                    kvittering.dokument.innrapporteringTrekk.navTrekkId ?: "Ingen Trekkid i kvittering",
                    kvittering.dokument.innrapporteringTrekk.kodeTrekkAlternativ,
                    session
                )
            }
            kvittering
        }
    }

    fun hentAlleTrekkSomIkkeErSendt(): List<UtleggstrekkTable> =
        dataSource.withTransaction { session ->
            repository.fetchTrekkNotSendt(session)
        }

    fun hentAllePerioderForTrekkId(trekk: UtleggstrekkTable): List<TrekkPeriodeTable> =
        dataSource.withTransaction { session ->
            repository.fetchAllPerioderForTrekk(trekk, session)
        }

    fun hentAllePerioderForTrekkVersjon(trekk:UtleggstrekkTable):List<TrekkPeriodeTable> =
        dataSource.withTransaction { session ->
            repository.fetchPerioderForTrekkVersion(trekk, session)
        }

    fun hentSisteSekvensnummer(): Int =
        dataSource.withTransaction { session ->
            repository.fetchLastSekvensnr(session)
        }

    fun lagreUtleggstrekk(trekkListe: List<Trekkpaalegg>) {
        dataSource.withTransaction { session ->
            trekkListe.filterNot { repository.doesTrekkExist(it.trekkid, it.sekvensnummer, it.trekkversjon, session) }
                .let { nyeTrekk ->
                    logger.info("Det er ${nyeTrekk.size} som skal lagres")
                    repository.saveAllNewUtleggstrekk(nyeTrekk, session)
                }
        }
    }

    fun lagreGenerertePerioder(perioder: List<TrekkPeriodeTable>){
        dataSource.withTransaction { session ->
            repository.savePerioder(perioder, session)
        }
    }

    fun lagreFeilkoderFraOS(kvitteringerMedFeilkoder: List<TrekkTilOppdrag>){
        if (kvitteringerMedFeilkoder.isNotEmpty()) {
            dataSource.withTransaction { session ->
                repository.saveFeilkoder(kvitteringerMedFeilkoder, session )
            }
        }
}}