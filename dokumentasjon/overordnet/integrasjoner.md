# Integrasjoner – sokos-utleggstrekk

## Oversikt

sokos-utleggstrekk integrerer med følgende eksterne systemer:

| System | Retning | Protokoll | Formål |
|--------|---------|-----------|--------|
| Skatteetaten (SKE) | Innkommende | REST / HTTPS | Hente trekkpålegg |
| Oppdrag Z (OS) | Utgående | IBM MQ / JMS | Sende innrapporteringstrekk |
| Oppdrag Z (OS) | Innkommende | IBM MQ / JMS | Motta kvitteringer |
| Maskinporten | Utgående | OAuth 2.0 | Autentisering mot SKE |
| PostgreSQL | Intern | JDBC | Tilstandslagring |
| Unleash | Utgående | REST | Feature toggles |
| Slack | Utgående | Webhooks | Varsling ved feil |
| Prometheus | Innkommende | HTTP | Metrics-scraping |

## Skatteetaten

- **API:** REST (HTTPS)
- **Autentisering:** Maskinporten (system-til-system OAuth 2.0 via systembruker)
- **Scope:** `skatteetaten:trekkpaalegg`
- **Paginering:** Inntil 2 500 trekk per kall, basert på sekvensnummer
- **Organisasjonsnummer:** NAV Økonomilinjen (995277670) – **ikke** NAVs hoved-orgnr

> **Viktig:** NAVs NAIS-installasjon får tildelt Maskinporten-bruker tilknyttet NAVs hoved-orgnr (889640782). For å hente trekkpålegg for NAVs brukere (ikke ansatte) må det brukes en **systembruker** registrert på Økonomilinjas orgnr. Systembrukeren vedlikeholdes av [sokos-systembruker-vedlikehold](https://github.com/navikt/sokos-systembruker-vedlikehold).

## Oppdrag Z (MQ – utgående)

- **Protokoll:** IBM MQ over JMS
- **Kø:** `QA.Q1_231.OB04_TREKK_FRASKATT_JSON` (prod tilsvarende)
- **Format:** JSON (serialisert `TrekkTilOppdrag`-objekt)
- **Innhold:** `InnrapporteringTrekk` med aksjonskode, perioder og betalingsinformasjon

## Oppdrag Z (MQ – kvitteringer)

- **Protokoll:** IBM MQ over JMS
- **Kø:** `QA.Q1_SOKOS_UTLEGGSTREKK.KVITTERING`
- **Format:** JSON
- **Innhold:** `nav_trekk_id` ved OK, feilkode og beskrivelse ved feil

## Maskinporten

- **Formål:** Utstedelse av tilgangstoken for API-kall mot Skatteetaten
- **Klient-ID:** Konfigurert i NAIS-hemmeligheter
- **JWK:** RSA-nøkkelpar (privat nøkkel i NAIS-hemmeligheter)
- **Well-known URL:** `https://maskinporten.no/.well-known/oauth-authorization-server`

## PostgreSQL

- **Formål:** Tilstandslagring for alle trekkpålegg og transaksjoner
- **Skjemamigrering:** Flyway
- **Tilkobling:** Via HikariCP (connection pool)
- **Plattform:** NAIS-administrert PostgreSQL (GCP Cloud SQL)

## Unleash

Tre feature toggles styrer hvilke deler av flyten som er aktive:

| Toggle | Beskrivelse |
|--------|-------------|
| `sokos-utleggstrekk.hent-fra-ske.enabled` | Aktiver henting fra Skatteetaten |
| `sokos-utleggstrekk.prosesser-utleggstrekk.enabled` | Aktiver behandling av trekk |
| `sokos-utleggstrekk.send-til-os.enabled` | Aktiver sending til Oppdrag Z |
