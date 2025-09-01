package no.nav.sokos.utleggstrekk.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry

object Metrics {
    val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    val appStateRunningFalse: Counter =
        Counter
            .builder("app_state_running_false")
            .description("app state running changed to false")
            .register(registry)

    val appStateReadyFalse: Counter =
        Counter
            .builder("app_state_ready_false")
            .description("app state ready changed to false")
            .register(registry)
}
