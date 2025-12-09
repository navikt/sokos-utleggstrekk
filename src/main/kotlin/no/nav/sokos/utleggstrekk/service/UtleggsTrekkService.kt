package no.nav.sokos.utleggstrekk.service

import com.ibm.mq.jakarta.jms.MQQueue
import com.ibm.msg.client.jakarta.wmq.WMQConstants
import mu.KotlinLogging

import no.nav.sokos.utleggstrekk.client.MAX_ANTALL
import no.nav.sokos.utleggstrekk.client.SkeClient
import no.nav.sokos.utleggstrekk.config.PropertiesConfig
import no.nav.sokos.utleggstrekk.config.TEAM_LOGS_MARKER
import no.nav.sokos.utleggstrekk.database.PostgresDataSource
import no.nav.sokos.utleggstrekk.database.RepositoryNy
import no.nav.sokos.utleggstrekk.database.model.TransaksjonOS
import no.nav.sokos.utleggstrekk.database.model.TransaksjonsStatus
import no.nav.sokos.utleggstrekk.domene.nav.TrekkAlternativ
import no.nav.sokos.utleggstrekk.domene.ske.Trekkpaalegg
import no.nav.sokos.utleggstrekk.domene.ske.Trekkstatus
import no.nav.sokos.utleggstrekk.metrics.Metrics
import no.nav.sokos.utleggstrekk.metrics.Metrics.set
import no.nav.sokos.utleggstrekk.metrics.Metrics.utleggstrekkFraSkatt
import no.nav.sokos.utleggstrekk.mq.JmsListenerService
import no.nav.sokos.utleggstrekk.mq.JmsProducerService
import no.nav.sokos.utleggstrekk.utils.DurationUtil.durationOf

class UtleggsTrekkService(
    private val repositoryNy: RepositoryNy = RepositoryNy(PostgresDataSource.dataSource),
    private val skeClient: SkeClient = SkeClient(),
    private val maxAntall: Int = MAX_ANTALL,
    private val mqProducer: JmsProducerService =
        JmsProducerService(
            targetQueue =
                MQQueue(PropertiesConfig.MQProperties().queueName).apply {
                    targetClient = WMQConstants.WMQ_CLIENT_NONJMS_MQ
                },
            replyQueue = JmsListenerService(repositoryNy).osKvitteringQueue,
        ),
) {
    private val logger = KotlinLogging.logger { }

    // Eksempel funksjon som kalles i schedulering
    suspend fun schedule() {
        lagreAlleNyeUtleggstrekk()
        BehandleTrekkServiceNy(repositoryNy).behandleTrekk()
        repositoryNy.getTransaksjonerTilOsSomIkkeErSendt().forEach { osTransaksjon -> sendTrekkTilOS(osTransaksjon) }
        // TODO: Fjerne når vi bekreftet at secure logger funker
        logger.info(marker = TEAM_LOGS_MARKER) {
            "Alle nye utleggstrekk er lagret."
        }
        repositoryNy.deleteOldData()
        calulateMetrics()
    }

    private suspend fun lagreAlleNyeUtleggstrekk() {
        do {
            val time = System.currentTimeMillis()
            val nyeUtleggsTrekk: List<Trekkpaalegg> = hentUtleggsTrekk()
            processTrekkpaalegg(nyeUtleggsTrekk)
            val duration = System.currentTimeMillis() - time
            if (nyeUtleggsTrekk.size > 0) {
                // Sekunder per tusen, men fordi duration er millsekunder trenger vi ikke dele igjen.
                Metrics.tidBruktPaaLagringAvUtleggstrekk.set(duration / (nyeUtleggsTrekk.size.toDouble()))
            }
        } while (nyeUtleggsTrekk.size >= maxAntall)
    }

    private suspend fun hentUtleggsTrekk(): List<Trekkpaalegg> {
        val sisteSekvensnr = repositoryNy.getLastSekvensnummer()
        return skeClient.hentUtleggstrekkFraSekvensnr(sisteSekvensnr)
    }

    private fun processTrekkpaalegg(trekkpaalegg: List<Trekkpaalegg>) {
        // Sortert for at vi ikke skal hoppe over noen i sekvens dersom vi feiler før alle er lagret.
        trekkpaalegg.sortedBy { it.sekvensnummer }.forEach { trekk ->
            try {
                repositoryNy.insertTrekkFraSkatt(trekk)
                utleggstrekkFraSkatt.inc()
            } catch (e: Exception) {
                logger.error("Kunne ikke lagre trekkpåleg sekvens #${trekk.sekvensnummer} ", e)
                // Kaster exception videre fordi vi ønsker å avslutte henting og lagring selv om MAX_ANTALL ikke er nådd
                throw e
            }
        }
    }

    private fun sendTrekkTilOS(transaksjonOS: TransaksjonOS) {
        runCatching {
            mqProducer.send(transaksjonOS.documentJson)
        }.onSuccess {
            repositoryNy.updateTransaksjonStatus(transaksjonOS.transaksjonsID, TransaksjonsStatus.SENDT)
        }.onFailure { exception ->
            logger.error(exception) { "Feil ved sending av dokument til OS: ${exception.message}" }
        }
    }

    private fun calulateMetrics() {
        val duration =
            durationOf {
                val utleggstrekkCounts = repositoryNy.countUtleggstrekk()
                Metrics.utleggstrekkAktive.set(utleggstrekkCounts[Trekkstatus.AKTIV] ?: 0)
                Metrics.utleggstrekkAvsluttede.set(utleggstrekkCounts[Trekkstatus.AVSLUTTET] ?: 0)

                val kvitterteTrekk = repositoryNy.countKvitterteTrekkTilOS()
                Metrics.aktiveTrekkKvittert.labelValues("prosenttrekk").set(kvitterteTrekk[TrekkAlternativ.LOPP] ?: 0)
                Metrics.aktiveTrekkKvittert.labelValues("beløpstrekk").set(kvitterteTrekk[TrekkAlternativ.LOPM] ?: 0)
            }
        Metrics.tidBruktMetrics.set(duration / 1000.0)
    }
}