package no.nav.sokos.utleggstrekk.metrics

import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.Counter

private const val NAMESPACE = "sokos_utleggstrekk"

object Metrics {
    val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    val appStateRunningFalse: Counter =
        Counter.build()
            .namespace(NAMESPACE)
            .name("app_state_running_false")
            .help("app state running changed to false")
            .register(registry.prometheusRegistry)

    val appStateReadyFalse: Counter =
        Counter.build()
            .namespace(NAMESPACE)
            .name("app_state_ready_false")
            .help("app state ready changed to false")
            .register(registry.prometheusRegistry)
}