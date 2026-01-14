package no.nav.sokos.utleggstrekk.service

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle.SHORT

import com.ibm.mq.jakarta.jms.MQQueue
import com.ibm.msg.client.jakarta.wmq.WMQConstants
import mu.KotlinLogging

import no.nav.sokos.utleggstrekk.client.MAX_ANTALL
import no.nav.sokos.utleggstrekk.client.SkeClient
import no.nav.sokos.utleggstrekk.config.PropertiesConfig
import no.nav.sokos.utleggstrekk.config.TEAM_LOGS_MARKER
import no.nav.sokos.utleggstrekk.config.jsonConfig
import no.nav.sokos.utleggstrekk.database.PostgresDataSource
import no.nav.sokos.utleggstrekk.database.RepositoryNy
import no.nav.sokos.utleggstrekk.database.model.TransaksjonOS
import no.nav.sokos.utleggstrekk.domene.nav.TrekkAlternativ
import no.nav.sokos.utleggstrekk.domene.nav.TrekkTilOppdrag
import no.nav.sokos.utleggstrekk.domene.nav.validate
import no.nav.sokos.utleggstrekk.domene.ske.Trekkpaalegg
import no.nav.sokos.utleggstrekk.domene.ske.Trekkstatus
import no.nav.sokos.utleggstrekk.domene.ske.validate
import no.nav.sokos.utleggstrekk.metrics.Metrics
import no.nav.sokos.utleggstrekk.metrics.Metrics.set
import no.nav.sokos.utleggstrekk.metrics.Metrics.utleggstrekkFraSkatt
import no.nav.sokos.utleggstrekk.mq.JmsListenerService
import no.nav.sokos.utleggstrekk.mq.JmsProducerService
import no.nav.sokos.utleggstrekk.unleash.UnleashIntegration
import no.nav.sokos.utleggstrekk.utils.DurationUtil.durationOf

class UtleggsTrekkService(
    private val repositoryNy: RepositoryNy = RepositoryNy(PostgresDataSource.dataSource),
    private val skeClient: SkeClient = SkeClient(),
    private val slackService: SlackService = SlackService.instance,
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
    private val featureToggles = UnleashIntegration()

    suspend fun schedule() {
        logger.info("Schedule started")
        if (featureToggles.isHentFraSKEEnabled()) {
            lagreAlleNyeUtleggstrekk()
        }
        if (featureToggles.isProsesserUtleggstrekkEnabled()) {
            BehandleTrekkServiceNy(repositoryNy).behandleTrekk()
        }
        if (featureToggles.isSendTilOSEnabled()) {
            repositoryNy.getTransaksjonerTilOsSomIkkeErSendt().forEach { osTransaksjon -> sendTrekkTilOS(osTransaksjon) }
        }
        // TODO: Fjerne når vi bekreftet at secure logger funker
        logger.info(marker = TEAM_LOGS_MARKER) {
            "Alle nye utleggstrekk er lagret."
        }
        repositoryNy.deleteOldData()
        calculateMetrics()
        slackService.sendCachedErrors("Trekk henting feil")
    }

    private suspend fun lagreAlleNyeUtleggstrekk() {
        do {
            val time = System.currentTimeMillis()
            val nyeUtleggsTrekk: List<Trekkpaalegg> = hentUtleggsTrekk()
            processTrekkpaalegg(nyeUtleggsTrekk)
            val duration = System.currentTimeMillis() - time
            if (nyeUtleggsTrekk.isNotEmpty()) {
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
                trekk.validate()
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
            jsonConfig.decodeFromString<TrekkTilOppdrag>(transaksjonOS.documentJson).validate()
            mqProducer.send(transaksjonOS.documentJson)
        }.onSuccess {
            repositoryNy.updateTransaksjonSendt(transaksjonOS.transaksjonsID)
        }.onFailure { exception ->
            logger.error(exception) { "Feil ved sending av dokument til OS: ${exception.message}" }
        }
    }

    suspend fun reportMissingKvittering() {
        val yesterday = LocalDateTime.now().minusDays(1)
        val formatter = DateTimeFormatter.ofLocalizedDateTime(SHORT)
        repositoryNy
            .getTransakjonerTilOsSomManglerKvittering()
            .filter { it.tidspunktSendt?.isBefore(yesterday) == true }
            .forEach {
                val header = "TransaksjonID mangler kvitteringen"
                val message = "TransaksjonID ${it.transaksjonsID} ble sendt ${it.tidspunktSendt?.format(formatter)} men vi har ikke mottatt kvitteringen."
                slackService.addError(header, message)
            }
        slackService.sendCachedErrors("Kvittering fra oppdrag uteblir")
    }

    fun calculateMetrics() {
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