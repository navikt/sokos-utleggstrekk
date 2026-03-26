# Metrics

**Pakke:** `no.nav.sokos.utleggstrekk.metrics`  
**Fil:** `metrics/Metrics.kt`

## Ansvar

Definerer og registrerer alle Prometheus-metrikker for applikasjonen. Eksponeres via `/internal/metrics`-endepunktet og kan hentes av Prometheus for overvåking i Grafana.

Alle metrikknavn prefixes med `sokos_utleggstrekk_`.

---

## Registry

```kotlin
val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
```

Brukes av `NaisApi` for å skrive ut alle metrikker i Prometheus-format.

---

## Tellere (Counter)

Teller totalt antall hendelser siden oppstart – verdien kan aldri gå ned.

| Felt                   | Metrikknavn                                   | Beskrivelse                                    |
|------------------------|-----------------------------------------------|------------------------------------------------|
| `utleggstrekkFraSkatt` | `sokos_utleggstrekk_utleggstrekk_fra_skatt`   | Antall trekkversjoner mottatt fra Skatteetaten |
| `trekkSendtTilOs`      | `sokos_utleggstrekk_trekk_sendt_til_os`       | Antall trekk sendt til Oppdrag Z               |
| `trekkKvittertForAvOS` | `sokos_utleggstrekk_trekk_kvittert_for_av_os` | Antall trekk Oppdrag Z har kvittert OK for     |
| `trekkAvvistAvOs`      | `sokos_utleggstrekk_trekk_avvist_av_os`       | Antall trekk Oppdrag Z har avvist              |

---

## Gauger (Gauge)

Viser gjeldende verdi – kan gå både opp og ned.

| Felt                               | Metrikknavn                                                | Label             | Beskrivelse                                                                |
|------------------------------------|------------------------------------------------------------|-------------------|----------------------------------------------------------------------------|
| `aktiveTrekkKvittert`              | `sokos_utleggstrekk_antall_aktive_trekk_kvittert_av_OS`    | `trekkalternativ` | Aktive trekk kvittert av OS, per alternativ (`prosenttrekk`/`beløpstrekk`) |
| `utleggstrekkAktive`               | `sokos_utleggstrekk_utleggstrekk_fra_skatt_aktive`         | –                 | Antall aktive trekkpålegg i databasen                                      |
| `utleggstrekkAvsluttede`           | `sokos_utleggstrekk__utleggstrekk_fra_skatt_avsluttet`     | –                 | Antall avsluttede trekkpålegg                                              |
| `tidBruktPaaLagringAvUtleggstrekk` | `sokos_utleggstrekk_tid_brukt_paa_lagring_av_utleggstrekk` | –                 | Millisekunder brukt på lagring per trekk                                   |
| `tidBruktMetrics`                  | `sokos_utleggstrekk_tid_brukt_paa_metrikker`               | –                 | Sekunder brukt på å beregne metrikker                                      |


