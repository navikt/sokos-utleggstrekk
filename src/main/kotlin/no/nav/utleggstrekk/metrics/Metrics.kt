package sokos.utleggstrekk.metrics

import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.Counter


object Metrics {
    val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    private const val NAMESPACE = "sokos_utleggstrekk"

    val numberOfUtleggstrekkMottatt: Counter = Counter.build()
        .namespace(NAMESPACE)
        .name("utleggstrekk_mottatt")
        .help("antall trekk mottatt fra SKE")
        .register(registry.prometheusRegistry)

    val numberOfUtleggstrekkSent: Counter = Counter.build()
        .namespace(NAMESPACE)
        .name("utleggstrekk_sendt")
        .help("antall trekk sendt til OS")
        .register(registry.prometheusRegistry)

    val appStateRunningFalse: Counter = Counter.build()
        .namespace(NAMESPACE)
        .name("app_state_running_false")
        .help("app state running changed to false")
        .register(registry.prometheusRegistry)

    val appStateReadyFalse: Counter = Counter.build()
        .namespace(NAMESPACE)
        .name("app_state_ready_false")
        .help("app state ready changed to false")
        .register(registry.prometheusRegistry)


}
