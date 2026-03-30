package no.nav.sokos.utleggstrekk.metrics

import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.prometheus.metrics.core.datapoints.GaugeDataPoint
import io.prometheus.metrics.core.metrics.Counter
import io.prometheus.metrics.core.metrics.Gauge

const val METRICS_NAMESPACE = "sokos_utleggstrekk"

object Metrics {
    val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    val utleggstrekkFraSkatt =
        counter(
            "utleggstrekk_fra_skatt",
            "Antall trekkversjoner mottatt fra skatt",
        )

    val trekkSendtTilOs =
        counter(
            "trekk_sendt_til_os",
            "Antall trekk sendt til os",
        )

    val trekkKvittertForAvOS =
        counter(
            "trekk_kvittert_for_av_os",
            "Antall trekk oppdragssystemet har kvittert OK for",
        )

    val trekkAvvistAvOs =
        counter(
            "trekk_avvist_av_os",
            "Antall trekk Oppdragssystemet har avvist",
        )

    val aktiveTrekkKvittert =
        gauge(
            "antall_aktive_trekk_kvittert_av_OS",
            "Antallet aktive trekk kvittert for av OS",
            "trekkalternativ",
        )

    val utleggstrekkAktive =
        gauge(
            "utleggstrekk_fra_skatt_aktive",
            "Antall aktive utleggstrekk",
        )

    // Leading underscore removed: Prometheus metric names must not start with underscore.
    val utleggstrekkAvsluttede =
        gauge(
            "utleggstrekk_fra_skatt_avsluttet",
            "Antall avsluttede utleggstrekk",
        )

    val tidBruktPaaLagringAvUtleggstrekk =
        gauge(
            "tid_brukt_paa_lagring_av_utleggstrekk",
            "Tiden det tar å lagre utleggstrekk per 1000",
        )

    val tidBruktMetrics =
        gauge(
            "tid_brukt_paa_metrikker",
            "Sekunder brukt på å beregne metrikker",
        )

    fun counter(name: String, helpText: String): Counter =
        Counter
            .builder()
            .name("${METRICS_NAMESPACE}_$name")
            .help(helpText)
            .withoutExemplars()
            .register(registry.prometheusRegistry)

    fun gauge(name: String, helpText: String): Gauge =
        Gauge
            .builder()
            .name("${METRICS_NAMESPACE}_$name")
            .help(helpText)
            .withoutExemplars()
            .register(registry.prometheusRegistry)

    fun gauge(name: String, helpText: String, labelNames: String): Gauge =
        Gauge
            .builder()
            .labelNames(labelNames)
            .name("${METRICS_NAMESPACE}_$name")
            .help(helpText)
            .withoutExemplars()
            .register(registry.prometheusRegistry)

    fun Gauge.set(l: Long) = set(l.toDouble())

    fun GaugeDataPoint.set(l: Long) = set(l.toDouble())
}
