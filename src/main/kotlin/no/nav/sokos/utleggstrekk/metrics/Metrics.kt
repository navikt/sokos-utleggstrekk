package no.nav.sokos.utleggstrekk.metrics

import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.prometheus.metrics.core.datapoints.GaugeDataPoint
import io.prometheus.metrics.core.metrics.Counter
import io.prometheus.metrics.core.metrics.Gauge

const val METRICS_NAMESPACE = "sokos_utleggstrekk"

object Metrics {
    val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    val appStateRunningFalse: Counter =
        Counter
            .builder()
            .name("app_state_running_false")
            .help("app state running changed to false")
            .withoutExemplars()
            .register(registry.prometheusRegistry)

    val appStateReadyFalse: Counter =
        Counter
            .builder()
            .name("app_state_ready_false")
            .help("app state ready changed to false")
            .withoutExemplars()
            .register(registry.prometheusRegistry)

    val utleggstrekkFraSkatt: Counter =
        Counter
            .builder()
            .name("${METRICS_NAMESPACE}_utleggstrekk_fra_skatt")
            .help("Antall trekkversjoner mottatt fra skatt")
            .withoutExemplars()
            .register(registry.prometheusRegistry)

    val trekkSendtTilOs: Counter =
        Counter
            .builder()
            .name("${METRICS_NAMESPACE}_trekk_sendt_til_os")
            .help("Antall trekk sendt til os")
            .withoutExemplars()
            .register(registry.prometheusRegistry)

    val trekkKvittertForAvOS: Counter =
        Counter
            .builder()
            .name("${METRICS_NAMESPACE}_trekk_kvittert_for_av_os")
            .help("Antall trekk oppdragssystemet har kvittert OK for")
            .withoutExemplars()
            .register(registry.prometheusRegistry)

    val trekkAvvistAvOs: Counter =
        Counter
            .builder()
            .name("${METRICS_NAMESPACE}_trekk_avvist_av_os")
            .help("Antall trekk Oppdragssystemet har avvist")
            .withoutExemplars()
            .register(registry.prometheusRegistry)

    val aktiveTrekkKvittert: Gauge =
        Gauge
            .builder()
            .name("${METRICS_NAMESPACE}_antall_aktive_trekk_kvittert_av_OS")
            .help("Antallet aktive trekk kvittert for av OS")
            .labelNames("trekkalternativ")
            .withoutExemplars()
            .register(registry.prometheusRegistry)

    val utleggstrekkAktive: Gauge =
        Gauge
            .builder()
            .name("${METRICS_NAMESPACE}_utleggstrekk_fra_skatt_aktive")
            .help("Antall aktive utleggstrekk")
            .withoutExemplars()
            .register(registry.prometheusRegistry)

    val utleggstrekkAvsluttede: Gauge =
        Gauge
            .builder()
            .name("${METRICS_NAMESPACE}_utleggstrekk_fra_skatt_avsluttet")
            .help("Antall avsluttede utleggstrekk")
            .withoutExemplars()
            .register(registry.prometheusRegistry)

    val tidBruktPaaLagringAvUtleggstrekk: Gauge =
        Gauge
            .builder()
            .name("${METRICS_NAMESPACE}_tid_brukt_på_lagring_av_utleggstrekk")
            .help("Tiden det tar å lagre utleggstrekk per 1000")
            .withoutExemplars()
            .register(registry.prometheusRegistry)

    val tidBruktMetrics: Gauge =
        Gauge
            .builder()
            .name("${METRICS_NAMESPACE}_tid_brukt_på_metrikker")
            .help("Sekunder brukt på å beregne metrikker")
            .withoutExemplars()
            .register(registry.prometheusRegistry)

    fun Gauge.set(l: Long) = set(l.toDouble())

    fun GaugeDataPoint.set(l: Long) = set(l.toDouble())
}
