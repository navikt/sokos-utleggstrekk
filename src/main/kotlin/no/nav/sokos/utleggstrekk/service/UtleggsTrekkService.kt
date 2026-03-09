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
import no.nav.sokos.utleggstrekk.database.Repository
import no.nav.sokos.utleggstrekk.database.model.SkattTrekkStatus
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
    private val repository: Repository = Repository(PostgresDataSource.dataSource),
    private val skeClient: SkeClient = SkeClient(),
    private val slackService: SlackService = SlackService.instance,
    private val maxAntall: Int = MAX_ANTALL,
    private val mqProducer: JmsProducerService =
        JmsProducerService(
            targetQueue =
                MQQueue(PropertiesConfig.mqProperties.queueName).apply {
                    targetClient = WMQConstants.WMQ_CLIENT_NONJMS_MQ
                },
            replyQueue = JmsListenerService(repository).osKvitteringQueue,
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
            BehandleTrekkService(repository).behandleTrekk()
        }
        if (featureToggles.isSendTilOSEnabled()) {
            repository.getTransaksjonerTilOsSomIkkeErSendt().forEach { osTransaksjon -> sendTrekkTilOS(osTransaksjon) }
        }
        repository.deleteOldData()
        calculateMetrics()
        slackService.sendCachedErrors("Trekk henting alert")
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
        val sisteSekvensnr = repository.getLastSekvensnummer()
        return skeClient.hentUtleggstrekkFraSekvensnr(sisteSekvensnr)
    }

    private fun processTrekkpaalegg(trekkpaalegg: List<Trekkpaalegg>) {
        // Sortert for at vi ikke skal hoppe over noen i sekvens dersom vi feiler før alle er lagret.
        trekkpaalegg.sortedBy { it.sekvensnummer }.forEach { trekk ->
            var status = SkattTrekkStatus.MOTTATT
            try {
                trekk.validate()
            } catch (e: IllegalArgumentException) {
                logger.error("Ugyldige verdier i trekk fra skatt med id ${trekk.trekkid}")
                logger.error(TEAM_LOGS_MARKER, "Ugyldige verdier i trekk fra skatt med id ${trekk.trekkid}", e)
                status = SkattTrekkStatus.AVVIST
            }
            try {
                repository.insertTrekkFraSkatt(trekk, status)
                utleggstrekkFraSkatt.inc()
            } catch (e: Exception) {
                logger.error("Kunne ikke lagre trekkpålegg sekvens #${trekk.sekvensnummer} ")
                logger.error(TEAM_LOGS_MARKER, "Kunne ikke lagre trekkpålegg sekvens #${trekk.sekvensnummer} ", e)
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
            repository.updateTransaksjonSendt(transaksjonOS.transaksjonsID)
        }.onFailure { exception ->
            slackService.addError("Feil ved sending", "Feil ved sending av dokument til OS")
            logger.error(TEAM_LOGS_MARKER, "Feil ved sending av dokument til OS", exception)
        }
    }

    suspend fun reportMissingKvittering() {
        val yesterday = LocalDateTime.now().minusDays(1)
        val formatter = DateTimeFormatter.ofLocalizedDateTime(SHORT)
        repository
            .getTransakjonerTilOsSomManglerKvittering()
            .filter { it.tidspunktSendt?.isBefore(yesterday) == true }
            .forEach {
                val header = "TransaksjonID mangler kvitteringen"
                val message = "Mangler kvittering for transaksjonID ${it.transaksjonsID} sendt ${it.tidspunktSendt?.format(formatter)}"
                slackService.addError(header, message)
            }
        slackService.sendCachedErrors("Kvittering fra oppdrag uteblir")
    }

    fun calculateMetrics() {
        val duration =
            durationOf {
                val utleggstrekkCounts = repository.countUtleggstrekk()
                Metrics.utleggstrekkAktive.set(utleggstrekkCounts[Trekkstatus.AKTIV] ?: 0)
                Metrics.utleggstrekkAvsluttede.set(utleggstrekkCounts[Trekkstatus.AVSLUTTET] ?: 0)

                val kvitterteTrekk = repository.countKvitterteTrekkTilOS()
                Metrics.aktiveTrekkKvittert.labelValues("prosenttrekk").set(kvitterteTrekk[TrekkAlternativ.LOPP] ?: 0)
                Metrics.aktiveTrekkKvittert.labelValues("beløpstrekk").set(kvitterteTrekk[TrekkAlternativ.LOPM] ?: 0)
            }
        Metrics.tidBruktMetrics.set(duration / 1000.0)
    }
}