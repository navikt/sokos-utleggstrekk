# UtleggsTrekkService

**Pakke:** `no.nav.sokos.utleggstrekk.service`  
**Fil:** `service/UtleggsTrekkService.kt`

## Ansvar

Hoved-orkestratoren for hele prosesseringssyklusen. Koordinerer henting fra Skatteetaten, behandling, sending til Oppdrag Z, kvitteringsrapportering og metrikker. Kalles av `UtleggstrekkScheduler` én gang per time.

## Avhengigheter

| Avhengighet            | Rolle                                   |
|------------------------|-----------------------------------------|
| `Repository`           | All databasetilgang                     |
| `SkeClient`            | Henter trekkpålegg fra Skatteetaten     |
| `BehandleTrekkService` | Transformerer trekkpålegg til OS-format |
| `JmsProducerService`   | Sender meldinger til IBM MQ             |
| `SlackService`         | Varsler om feil til Slack               |
| `UnleashIntegration`   | Feature toggles for å styre flyten      |

---

## Funksjoner

### `suspend fun schedule()`

**Synlighet:** public  
**Kaller:** Scheduleren én gang per time

Koordinerer hele prosesseringssyklusen i rekkefølge:

1. Hvis `hent-fra-ske`-toggle er på: kaller `lagreAlleNyeUtleggstrekk()`
2. Hvis `prosesser-utleggstrekk`-toggle er på: kaller `BehandleTrekkService.behandleTrekk()`
3. Hvis `send-til-os`-toggle er på: sender alle usendte transaksjoner via `sendTrekkTilOS()`
4. Sletter gamle data (`repository.deleteOldData()`)
5. Beregner og oppdaterer Prometheus-metrikker (ikke i testmiljø)
6. Sender eventuelle bufrede Slack-feil

---

### `private suspend fun lagreAlleNyeUtleggstrekk()`

Henter og lagrer alle nye trekkpålegg fra Skatteetaten i en løkke frem til færre enn `MAX_ANTALL` (2 500) trekk returneres (dvs. ingen flere å hente).

For hvert batch:
- Kaller `hentUtleggsTrekk()` for å hente fra SKE
- Kaller `processTrekkpaalegg()` for å validere og lagre
- Registrerer tidsbruk per trekk i Prometheus (`tidBruktPaaLagringAvUtleggstrekk`)

---

### `private suspend fun hentUtleggsTrekk(): List<Trekkpaalegg>`

Slår opp siste kjente sekvensnummer i databasen og ber `SkeClient` om alle nyere trekkpålegg.

**Returnerer:** Liste med `Trekkpaalegg` fra Skatteetaten

---

### `private suspend fun processTrekkpaalegg(trekkpaalegg: List<Trekkpaalegg>)`

Validerer og lagrer hvert trekkpålegg i databasen, sortert på sekvensnummer for å bevare rekkefølgen.

For hvert trekk:
- Kaller `trekk.validate()` – setter status til `AVVIST` og varsler Slack ved valideringsfeil
- Kaller `repository.insertTrekkFraSkatt(trekk, status)` – kaster exception videre ved databasefeil (stopper batchen)
- Inkrementerer metrikken `utleggstrekkFraSkatt`

**Feilhåndtering:** Valideringsfeil setter `AVVIST` og fortsetter til neste trekk. Databasefeil avbryter hele batchen.

---

### `private fun sendTrekkTilOS(transaksjonOS: TransaksjonOS)`

Sender ett ferdigbehandlet dokument til Oppdrag Z via IBM MQ.

1. Deserialiserer `documentJson` til `TrekkTilOppdrag` og validerer innholdet
2. Kaller `mqProducer.send(documentJson)` for å legge melding på køen
3. Ved suksess: kaller `updateTransactionAfterSending()` for å sette `transaksjon_status = SENDT`
4. Ved feil: legger til Slack-feil; setter `transaksjon_status = VALIDERINGSFEIL` hvis det er en `IllegalArgumentException`

---

### `private fun updateTransactionAfterSending(transaksjonId: String)`

Oppdaterer `transaksjon_status` til `SENDT` i databasen. Legger til Slack-feil dersom databaseoppdateringen feiler (meldingen ble likevel sendt til MQ).

---

### `suspend fun reportMissingKvittering()`

**Synlighet:** public  
Finner transaksjoner som ble sendt for mer enn 24 timer siden uten å ha mottatt kvittering, og rapporterer disse til Slack.

Steg:
1. Henter alle transaksjoner med `kvittering_status = IKKE_MOTTATT` og `transaksjon_status = SENDT`
2. Filtrerer på de som ble sendt for mer enn 1 dag siden
3. Legger til én Slack-feil per transaksjon med `transaksjonsID` og tidspunkt
4. Sender alle bufrede feil til Slack

---

### `fun calculateMetrics()`

**Synlighet:** public  
Oppdaterer alle Prometheus-gauger med gjeldende tall fra databasen:

| Metrikk                               | Kilde                                         |
|---------------------------------------|-----------------------------------------------|
| `utleggstrekkAktive`                  | `repository.countUtleggstrekk()[AKTIV]`       |
| `utleggstrekkAvsluttede`              | `repository.countUtleggstrekk()[AVSLUTTET]`   |
| `aktiveTrekkKvittert["prosenttrekk"]` | `repository.countKvitterteTrekkTilOS()[LOPP]` |
| `aktiveTrekkKvittert["beløpstrekk"]`  | `repository.countKvitterteTrekkTilOS()[LOPM]` |
| `tidBruktMetrics`                     | Målt tid for selve beregningen                |
