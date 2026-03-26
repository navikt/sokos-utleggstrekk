# Kvittering og feilhåndtering

## Hva gjør denne funksjonen?

Etter at innrapporteringstrekk er sendt til Oppdrag Z, sender Oppdrag Z kvitteringer tilbake. sokos-utleggstrekk lytter på kvitteringskøen og lagrer resultatet for hvert dokument.

## Mottak av kvitteringer

`JmsListenerService` lytter **kontinuerlig** på `MQ_REPLY_QUEUE_NAME` – uavhengig av scheduleren.

### Vellykket kvittering (OK)

```
kvittering_status = OK
nav_trekk_id      = <ID tildelt av Oppdrag Z>
```

`nav_trekk_id` er Oppdrag Zs interne referanse til trekket og lagres for sporbarhet.

### Feilkvittering

```
kvittering_status = FEIL
feilkode          = <kode fra Oppdrag Z>
beskrivelse       = <tekstlig feilbeskrivelse>
```

Feildetaljer lagres i `feilmelding`-tabellen og det sendes et varsel til Slack.

### Ukjent kvittering

Dersom kvitteringen ikke kan kobles til en kjent transaksjon, settes:
```
kvittering_status = UKJENT
```

## Rapportering av manglende kvitteringer

Mot slutten av hver prosesseringssyklus sjekkes det om det finnes transaksjoner som:
- Har `transaksjon_status = SENDT`
- Har `kvittering_status = IKKE_MOTTATT`
- Ble sendt for mer enn forventet tid siden

Disse rapporteres til Slack som en advarsel om mulig problemer med MQ-kommunikasjonen.

## Mulige kvitteringsstatuser

| Status | Beskrivelse |
|--------|-------------|
| `IKKE_MOTTATT` | Ingen kvittering mottatt ennå |
| `OK` | Oppdrag Z aksepterte dokumentet |
| `FEIL` | Oppdrag Z avviste dokumentet med feilkode |
| `UKJENT` | Kvittering mottatt men kunne ikke kobles til kjent transaksjon |

## Slack-varsler

Følgende situasjoner utløser Slack-varsler:

| Hendelse | Kanal |
|----------|-------|
| Trekkpålegg avvist under validering | `SOKOS_UTLEGGSTREKK_SLACK_WEBHOOK_URL` |
| Feilkvittering fra Oppdrag Z | `SOKOS_UTLEGGSTREKK_SLACK_WEBHOOK_URL` |
| Manglende kvittering etter timeout | `SOKOS_UTLEGGSTREKK_SLACK_WEBHOOK_URL` |

## Prometheus-metrics

Antall transaksjoner per `kvittering_status` eksponeres som Prometheus-metrics på `/internal/metrics` og kan overvåkes i Grafana.
