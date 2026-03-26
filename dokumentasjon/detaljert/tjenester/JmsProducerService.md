# JmsProducerService

**Pakke:** `no.nav.sokos.utleggstrekk.mq`  
**Fil:** `mq/JmsProducerService.kt`

## Ansvar

Sender JSON-meldinger til IBM MQ. Bruker JMS med transaksjoner for å garantere at meldinger enten sendes helt eller rulles tilbake ved feil.

## Avhengigheter

| Avhengighet                    | Rolle                                       |
|--------------------------------|---------------------------------------------|
| `MQConfig.connectionFactory()` | Oppretter tilkobling til IBM MQ             |
| `targetQueue`                  | Køen det sendes til (`MQ_QUEUE_NAME`)       |
| `replyQueue`                   | Kvitteringskøen som settes som `JMSReplyTo` |

## Konfigurasjon

Opprettes med `SESSION_TRANSACTED` – dvs. at alle sendinger skjer i en JMS-transaksjon som må committes eksplisitt.

---

## Funksjoner

### `fun send(payload: String)`

**Synlighet:** public  
Sender en tekstmelding til `targetQueue`.

**Steg:**
1. Oppretter en `TextMessage` fra `payload`
2. Kaller `producer.send(targetQueue, message)`
3. Kaller `jmsContext.commit()` for å bekrefte transaksjonen
4. Inkrementerer metrikken `trekkSendtTilOs`
5. Ved feil: kaller `jmsContext.rollback()` og kaster `JMSException`

**Parametere:**
| Parameter | Beskrivelse |
|-----------|-------------|
| `payload` | JSON-streng som skal sendes (serialisert `TrekkTilOppdrag`) |

**Kaster:** `JMSException` ved sendingsfeil (etter rollback).

---

## MQ-konfigurasjon

- `JMSReplyTo` settes til `replyQueue` slik at Oppdrag Z vet hvilken kø kvitteringen skal sendes til
- `targetClient = WMQ_CLIENT_NONJMS_MQ` sørger for kompatibilitet med ikke-JMS-klienter
- Exception listener logger MQ-kommunikasjonsfeil til teamlogger
