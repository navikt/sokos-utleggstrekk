# Tjenesteklasser – sokos-utleggstrekk

Dokumentasjon for alle service-, client- og hjelpeklasser i applikasjonen.

## Innhold

| Fil                                                                  | Klasse                          | Ansvar                                         |
|----------------------------------------------------------------------|---------------------------------|------------------------------------------------|
| [UtleggsTrekkService.md](UtleggsTrekkService.md)                     | `UtleggsTrekkService`           | Hoved-orkestrering av prosesseringssyklusen    |
| [BehandleTrekkService.md](BehandleTrekkService.md)                   | `BehandleTrekkService`          | Transformasjon av trekkpålegg til OS-format    |
| [SlackService.md](SlackService.md)                                   | `SlackService`                  | Buffering og sending av Slack-varsler          |
| [SkeClient.md](SkeClient.md)                                         | `SkeClient`                     | REST-klient mot Skatteetatens API              |
| [JmsProducerService.md](JmsProducerService.md)                       | `JmsProducerService`            | Sending av meldinger til IBM MQ                |
| [JmsListenerService.md](JmsListenerService.md)                       | `JmsListenerService`            | Lytting og prosessering av kvitteringer fra MQ |
| [UtleggstrekkScheduler.md](UtleggstrekkScheduler.md)                 | `UtleggstrekkScheduler`         | Tidsstyrt kjøring av prosesseringssyklusen     |
| [Metrics.md](Metrics.md)                                             | `Metrics`                       | Prometheus-metrics og gauger                   |
| [MaskinportenAccessTokenClient.md](MaskinportenAccessTokenClient.md) | `MaskinportenAccessTokenClient` | OAuth 2.0-token fra Maskinporten               |
| [UnleashIntegration.md](UnleashIntegration.md)                       | `UnleashIntegration`            | Feature toggles via Unleash                    |
