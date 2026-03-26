# BehandleTrekkService

**Pakke:** `no.nav.sokos.utleggstrekk.service`  
**Fil:** `service/BehandleTrekkService.kt`

## Ansvar

Transformerer trekkpålegg lagret i databasen (status `MOTTATT`) til innrapporteringstrekk klare for sending til Oppdrag Z. Håndterer splitting i trekk-alternativ (LOPM/LOPP), beregning av aksjonskode og periode-logikk. Se også [perioder.md](../perioder.md) for detaljert forklaring av periodebehandlingen.

## Avhengigheter

| Avhengighet    | Rolle                                             |
|----------------|---------------------------------------------------|
| `Repository`   | Henter og lagrer trekk, perioder og transaksjoner |
| `SlackService` | Varsler om trekk uten perioder etter behandling   |

---

## Funksjoner

### `fun behandleTrekk()`

**Synlighet:** public  
**Innganspunkt** kalt av `UtleggsTrekkService`.

Henter alle trekk-IDer som skal behandles fra databasen og prosesserer hvert enkelt i en databasetransaksjon:

1. Henter `TrekkFraSkatt` og gjeldende status
2. Hvis status er `REPETERES`: sjekker om det finnes en nyere versjon av samme trekk. Hvis ja, settes status til `HOPPET_OVER` og behandlingen avbrytes
3. Kaller `lagTrekkDokument()` for å lage ett eller to OS-dokumenter
4. Lagrer hvert dokument i `transaksjon_os` og setter trekk-status til `BEHANDLET`
5. Ved exception: setter status til `AVVIST` og logger feilen

**Feilhåndtering:** Feil i ett trekk stopper ikke behandlingen av de neste – hvert trekk er i sin egen transaksjon.

---

### `private fun lagTrekkDokument(trekk, session): List<TrekkTilOppdrag>`

Koordinerer produksjon av OS-dokumenter for ett trekk:

1. Sjekker hvilke trekk-alternativ (LOPM/LOPP) OS allerede kjenner for dette trekket
2. Kaller `nyePerioderTilOS()` for å beregne hvilke perioder som skal sendes
3. For hvert alternativ lages ett dokument med riktig aksjonskode:
   - `NY` hvis alternativet ikke er kjent i OS fra før
   - `ENDR` hvis alternativet allerede eksisterer i OS
4. Avsluttede trekk (`trekkstatus = AVSLUTTET`) får tomme periodelister

**Returnerer:** 1–2 `TrekkTilOppdrag`-objekter (ett per alternativ)

---

### `private fun nyePerioderTilOS(trekkFraSkatt, session): PerioderTilOS`

Kjernen i periodelogikken. Beregner hvilke perioder som faktisk skal sendes til OS. Se [perioder.md](../perioder.md) for fullstendig forklaring.

**Steg:**
1. Henter SKE-perioder fra DB og justerer datoer til månedgrenser (`mapNewFomTom()`)
2. Slår fast alle aktuelle trekk-alternativ (fra perioder + kjente OS-alternativ)
3. Henter eksisterende OS-perioder og filtrerer vekk foreldede (`obsoleted()`)
4. Finner perioder i OS som ikke lenger finnes i SKE → lager nulleringer (`sats = 0.0`)
5. Finner nye SKE-perioder som OS ikke kjenner → legger til med riktig sats
6. Varsler Slack hvis et ikke-avsluttet trekk resulterer i ingen perioder

**Returnerer:** `PerioderTilOS` med LOPM- og LOPP-lister

---

### `private fun lagTrekkDokument(trekkFraSkatt, trekkalternativ, aksjonskode, perioderTilOS): TrekkTilOppdrag`

Bygger selve `TrekkTilOppdrag`-objektet som serialiseres og sendes til OS.

Henter betalingsinformasjon og bygger `InnrapporteringTrekk` med:

| Felt              | Kilde                                                 |
|-------------------|-------------------------------------------------------|
| `aksjonskode`     | Parameter (NY/ENDR/OPPH)                              |
| `kreditorIdTss`   | TSS-ID løst fra betalingsinformasjon                  |
| `kreditorTrekkId` | trekkid konvertert + `-P`/`-M`-suffiks (maks 35 tegn) |
| `kreditorsRef`    | saksnummer (trunkert til 30 tegn)                     |
| `debitorId`       | skyldner (fødselsnummer)                              |
| `kid`             | kidnummer fra betalingsinformasjon                    |
| `gyldigTomDato`   | dagens dato hvis trekket er avsluttet, ellers `null`  |
| `perioder`        | liste med justerte perioder, `null` hvis tom          |
| `transaksjonsId`  | ny UUID generert per dokument                         |

**Kaster:** `IllegalStateException` hvis betalingsinformasjon mangler for trekket.

---

### `private fun obsoleted(allePerioder, it): Boolean`

Avgjør om en OS-periode er foreldet og skal ignoreres ved sammenligning.

En periode er foreldet hvis én av følgende er sann:

| Betingelse                                       | Forklaring                                  |
|--------------------------------------------------|---------------------------------------------|
| `it.sats == 0.0`                                 | Perioden er allerede nullert                |
| `it.isExpired()`                                 | `periodeTomDato` er passert                 |
| Nyere rad med samme fom/tom og `sats=0.0` finnes | Perioden er overstyrt av en nyere nullering |

---

### `private fun TrekkFraSkatt.erAvsluttet(): Boolean`

Extension function som returnerer `true` hvis `trekkstatus == "AVSLUTTET"`.
