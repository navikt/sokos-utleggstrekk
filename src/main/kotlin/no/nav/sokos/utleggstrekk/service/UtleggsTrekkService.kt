package no.nav.sokos.utleggstrekk.service

import java.util.UUID

import com.ibm.mq.jakarta.jms.MQQueue
import com.ibm.msg.client.jakarta.wmq.WMQConstants
import com.zaxxer.hikari.HikariDataSource
import mu.KotlinLogging

import no.nav.sokos.utleggstrekk.client.SkeClient
import no.nav.sokos.utleggstrekk.config.PropertiesConfig
import no.nav.sokos.utleggstrekk.config.jsonConfig
import no.nav.sokos.utleggstrekk.database.PostgresDataSource
import no.nav.sokos.utleggstrekk.database.RepositoryNy
import no.nav.sokos.utleggstrekk.database.model.TransaksjonsStatus
import no.nav.sokos.utleggstrekk.database.model.UtleggstrekkTable
import no.nav.sokos.utleggstrekk.domene.nav.DokumentTilOppdrag
import no.nav.sokos.utleggstrekk.domene.nav.OSDto
import no.nav.sokos.utleggstrekk.domene.ske.Trekkpaalegg
import no.nav.sokos.utleggstrekk.mq.JmsListenerService
import no.nav.sokos.utleggstrekk.mq.JmsProducerService

class UtleggsTrekkService(
    private val dataSource: HikariDataSource = PostgresDataSource.dataSource,
    private val behandleTrekkService: BehandleTrekkService = BehandleTrekkService(DatabaseService(dataSource)),
    private val skeClient: SkeClient = SkeClient(),
    private val mqProducer: JmsProducerService =
        JmsProducerService(
            targetQueue =
                MQQueue(PropertiesConfig.MQProperties().queueName).apply {
                    targetClient = WMQConstants.WMQ_CLIENT_NONJMS_MQ
                },
            replyQueue =
                JmsListenerService().osKvitteringQueue,
        ),
) {
    private val logger = KotlinLogging.logger { }

    // Eksempel funksjon som kalles i schedulering
    suspend fun run() {
        val nyeUtleggsTrekk: List<Trekkpaalegg> = hentUtleggsTrekk()
        // Lagrer i FraSkatt, Periode, Betalingsinformasjon
        // Vi må lagre "noe" (varkuleklasse) som har status MOTTATT eller noe sånt
        processTrekkpaalegg(nyeUtleggsTrekk)

        val trekktilSending = behandleTrekkService.lagTrekkSomSkalSendes()
        logger.info { "Det er ${trekktilSending.size} trekk som skal sendes" }
        sendTrekkTilOS(trekktilSending)
    }

    private suspend fun hentUtleggsTrekk(): List<Trekkpaalegg> {
        val sisteSekvensnr = dataSource.withTransaction { session -> RepositoryNy.getLastSekvensnummer(session) }
        return skeClient.hentUtleggstrekkFraSekvensnr(sisteSekvensnr)
    }

    private fun processTrekkpaalegg(trekkpaalegg: List<Trekkpaalegg>) {
        saveTrekkpalegg(trekkpaalegg)
    }

    private fun saveTrekkpalegg(trekkpaalegg: List<Trekkpaalegg>) {
        trekkpaalegg.forEach { trekk ->
            dataSource.withTransaction { session ->
                RepositoryNy.insertTrekkFraSkatt(trekk, session)
            }
        }
    }

    private fun sendTrekkTilOS(trekkTilOppdragMap: Map<UtleggstrekkTable, List<DokumentTilOppdrag>>) {
        trekkTilOppdragMap
            .forEach {
                sendTrekkTilOS(it.value, it.key.trekkidSke)
            }
    }

    private fun sendTrekkTilOS(trekkMeldinger: List<DokumentTilOppdrag>, trekkidFraSkatt: String) {
        trekkMeldinger
            .forEach { melding ->
                val dto =
                    OSDto(
                        transaksjonsID = UUID.randomUUID().toString(),
                        fraSkattID = trekkidFraSkatt,
                        aksjonskode = melding.dokument.innrapporteringTrekk.aksjonskode,
                        trekkAlternativ = melding.dokument.innrapporteringTrekk.kodeTrekkAlternativ,
                    )
                dataSource.withTransaction { session ->
                    RepositoryNy.insertTransaksjonTilOs(dto, session)
                }
                runCatching {
                    mqProducer.send(jsonConfig.encodeToString(melding))
                }.onSuccess {
                    dataSource.withTransaction { session ->
                        RepositoryNy.updateTransaksjonStatus(melding.dokument.transaksjonsId, TransaksjonsStatus.SENDT, session)
                    }
                }.onFailure { exception ->
                    logger.error(exception) { "Feil ved sending av dokument til OS: ${exception.message}" }
                }
            }
    }
}
