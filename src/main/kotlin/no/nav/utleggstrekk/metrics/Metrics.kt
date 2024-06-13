package sokos.utleggstrekk.metrics

import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.Counter


object Metrics {
    val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    private const val NAMESPACE = "sokos_ske_krav"

    val numberOfKravRead: Counter = Counter.build()
        .namespace(NAMESPACE)
        .name("krav_lest")
        .help("antall krav Lest fra fil")
        .register(registry.prometheusRegistry)

    val numberOfKravSent: Counter = Counter.build()
        .namespace(NAMESPACE)
        .name("krav_sendt")
        .help("antall krav sendt til endepunkt")
        .register(registry.prometheusRegistry)

    val numberOfKravResent: Counter = Counter.build()
        .namespace(NAMESPACE)
        .name("krav_resendt")
        .help("antall krav resendt")
        .register(registry.prometheusRegistry)

    val typeKravSent: Counter = Counter.build()
        .namespace(NAMESPACE)
        .name("type_krav_sendt")
        .help("type krav sendt til endepunkt")
        .labelNames("kravtype")
        .register(registry.prometheusRegistry)

    val fileValidationError: Counter = Counter.build()
        .namespace(NAMESPACE)
        .name("filvalidering_feil")
        .help("feil i validering av fil")
        .labelNames("fileName", "message")
        .register(registry.prometheusRegistry)

    val lineValidationError: Counter = Counter.build()
        .namespace(NAMESPACE)
        .name("linjevalidering_feil")
        .help("feil i validering av linje")
        .labelNames("fileName", "message")
        .register(registry.prometheusRegistry)

    val requestError: Counter = Counter.build()
        .namespace(NAMESPACE)
        .name("innsending_av_krav_feil")
        .help("feil i innsending av krav")
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
