# JmsListenerService

**Pakke:** `no.nav.sokos.utleggstrekk.mq`  
**Fil:** `mq/JmsListenerService.kt`

## Ansvar

Lytter kontinuerlig på kvitteringskøen fra Oppdrag Z og prosesserer innkommende kvitteringer. Oppdaterer databasen med kvitteringsstatus og `nav_trekk_id`, og varsler Slack ved feil. Meldinger som ikke kan prosesseres sendes til en BOQ-kø (Back-Out Queue).

## Avhengigheter

| Avhengighet                    | Rolle                                     |
|--------------------------------|-------------------------------------------|
| `Repository`                   | Lagrer kvitteringsstatus og feilmeldinger |
| `SlackService`                 | Varsler om prosesseringsfeil              |
| `JmsProducerService` (BOQ)     | Sender feilede meldinger til back-out-kø  |
| `MQConfig.connectionFactory()` | Oppretter MQ-tilkobling                   |

## Køer

| Kø                                          | Retning     | Beskrivelse                          |
|---------------------------------------------|-------------|--------------------------------------|
| `osKvitteringQueue` (`MQ_REPLY_QUEUE_NAME`) | Innkommende | Kvitteringer fra Oppdrag Z           |
| BOQ (`MQ_REPLY_BOQ_QUEUE_NAME`)             | Utgående    | Meldinger som ikke kunne prosesseres |

---

## Initialisering (`init`-blokk)

Opprettet med `CLIENT_ACKNOWLEDGE` (meldingen bekreftes manuelt etter vellykket prosessering):
1. Starter JMS-konteksten (`jmsContext.start()`)
2. Registrerer exception listener
3. Registrerer meldingslytter: `createConsumer(osKvitteringQueue).setMessageListener { onReceipt(it) }`

---

## Funksjoner

### `private fun onReceipt(message: Message)`

Kalles asynkront for hver innkommende melding på kvitteringskøen.

**Vellykket flyt:**
1. Henter meldingstekst (`String`)
2. Validerer strengen
3. Deserialiserer til `KvitteringFraOppdrag`
4. Kaller `receipt.validate()`
5. Kaller `processReceipt(receipt)` for å oppdatere databasen
6. Kaller `message.acknowledge()` for å bekrefte meldingen

**Feilflyt:**
- Logger feil og `jmsMessageID` til Slack-buffer
- Hvis feilen **ikke** er `MessageFormatException`: sender rå meldingstekst til BOQ og bekrefter meldingen (for å fjerne den fra kvitteringskøen)
- Sender bufrede Slack-feil asynkront

---

### `private fun processReceipt(receipt: KvitteringFraOppdrag)`

Lagrer kvitteringsresultatet i databasen.

**Steg:**
1. Bestemmer `KvitteringStatus` fra `receipt.mmel?.alvorlighetsgrad`
2. Kaller `repository.updateReceiptStatusOfTransaksjon()` med:
   - `transaksjonsId` fra kvitteringen
   - `kvitteringStatus`
   - `navTrekkId` (Oppdrag Zs interne referanse)
3. Hvis `kvitteringStatus == FEIL`:
   - Lagrer feilmelding i databasen
   - Kaller `logError()` for å varsle Slack
   - Inkrementerer metrikken `trekkAvvistAvOs`
4. Ellers: inkrementerer metrikken `trekkKvittertForAvOS`

---

### `private fun logError(receipt: KvitteringFraOppdrag)`

Logger og bufrer Slack-varsel for kvitteringer med feil.

- Maskerer fødselsnummer (11-sifrede tall) i feilbeskrivelsen med `[fødselsnummer]`
- Legger til Slack-feil med `kreditorTrekkId`, `transaksjonsId`, feilkode og maskert beskrivelse
