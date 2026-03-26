# Sending til Oppdrag Z

## Hva gjør denne funksjonen?

Ferdigbehandlede innrapporteringstrekk sendes til Oppdrag Z over IBM MQ som JSON-meldinger.

## Hvordan fungerer det?

### Hent klare dokumenter

Alle `TransaksjonOS`-rader med `transaksjon_status = IKKE_SENDT` hentes fra databasen.

### Bygg melding

Hver transaksjon serialiseres til en `TrekkTilOppdrag`-JSON:

```json
{
  "dokument": {
    "transaksjonsId": "<UUID>",
    "innrapporteringTrekk": {
      "aksjonskode": "NY",
      "kreditorTrekkId": "550e8400e29b41d4a716446655440000-P",
      "kodeTrekkAlternativ": "LOPP",
      "debitorId": "12345678901",
      "kid": "1234567890123",
      "kilde": "SOKOSUTLEGG",
      "perioder": {
        "periode": [
          { "periodeFomDato": "2024-01-01", "periodeTomDato": "2024-03-31", "sats": 15.0 },
          { "periodeFomDato": "2024-04-01", "periodeTomDato": null, "sats": 10.0 }
        ]
      }
    }
  }
}
```

### Send over MQ

Meldingen legges på køen `MQ_QUEUE_NAME` via IBM MQ / JMS. `JMSCorrelationID` settes til `transaksjonsId` for sporbarhet.

### Marker som sendt

Etter vellykket sending settes `transaksjon_status = SENDT` og `tidspunkt_sendt` oppdateres.

## MQ-køer

| Kø | Retning | Formål |
|----|---------|--------|
| `QA.Q1_231.OB04_TREKK_FRASKATT_JSON` | Utgående | Sende innrapporteringstrekk |
| `QA.Q1_SOKOS_UTLEGGSTREKK.KVITTERING` | Innkommende | Motta kvitteringer |

## Kanttilfeller

| Situasjon | Håndtering |
|-----------|------------|
| MQ-tilkobling feiler | Transaksjonen forblir `IKKE_SENDT`, prøves igjen neste syklus |
| Melding sendt men status ikke oppdatert | Kan resultere i dobbel-sending – idempotent design anbefales |

## Feature toggle

Denne funksjonen kan slås av/på med Unleash-toggle:
```
sokos-utleggstrekk.send-til-os.enabled
```
