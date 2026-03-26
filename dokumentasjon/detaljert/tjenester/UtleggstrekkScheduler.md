# UtleggstrekkScheduler

**Pakke:** `no.nav.sokos.utleggstrekk.scheduling`  
**Fil:** `scheduling/UtleggstrekkScheduler.kt`

## Ansvar

Tidsstyrt kjøring av koroutine-baserte oppgaver. Støtter både time-basert (`scheduleHourlyAt`) og dag-basert (`scheduleDailyAt`) planlegging. Etter at en oppgave er ferdig planlegges neste kjøring automatisk.

Brukes i `Application.kt` til å starte `UtleggsTrekkService.schedule()` én gang per time.

---

## Funksjoner

### `fun scheduleHourlyAt(minute, second, name, task)`

**Synlighet:** public  
Planlegger en oppgave til å kjøre **hvert minutt på timen** på et gitt minutt og sekund.

**Parametere:**
| Parameter | Type | Beskrivelse |
|-----------|------|-------------|
| `minute` | `Int` | Minuttet på timen oppgaven skal starte (f.eks. `45`) |
| `second` | `Int` | Sekund (standard `0`) |
| `name` | `String?` | Valgfritt navn for logging |
| `task` | `suspend () -> Unit` | Koroutine-funksjonen som kjøres |

**Eksempel:** `scheduleHourlyAt(minute = 45)` → kjøres kl. XX:45:00 hver time.

---

### `fun scheduleDailyAt(hour, minute, name, task)`

**Synlighet:** public  
Planlegger en oppgave til å kjøre **én gang per dag** på et gitt tidspunkt.

**Parametere:**
| Parameter | Type | Beskrivelse |
|-----------|------|-------------|
| `hour` | `Int` | Time på dagen (f.eks. `6` for kl. 06:00) |
| `minute` | `Int` | Minutt (standard `0`) |
| `name` | `String?` | Valgfritt navn for logging |
| `task` | `suspend () -> Unit` | Koroutine-funksjonen som kjøres |

---

### `private fun scheduleNext(hour, minute, second, name, task)`

Intern funksjon som beregner neste kjøretidspunkt og planlegger oppgaven med `ScheduledExecutorService`.

**Logikk:**
1. Beregner neste kjøretidspunkt basert på angitt time/minutt/sekund
2. Hvis tidspunktet allerede er passert: hopper til neste time (hourly) eller neste dag (daily)
3. Planlegger kjøring med `executor.schedule(delay, MILLISECONDS)`
4. Etter oppgaven er ferdig: kaller `scheduleNext()` rekursivt for å planlegge neste kjøring (med mindre `stop()` er kalt)

**Feilhåndtering:** Exceptions fra oppgaven fanges, logges og stopper ikke fremtidige kjøringer.

---

### `suspend fun stop(timeout: Duration = 30s)`

**Synlighet:** public  
Stopper scheduleren ryddig:
1. Setter `stopped = true` (hindrer planlegging av nye kjøringer)
2. Avbryter planlagt fremtidig kjøring (`future?.cancel(false)`)
3. Venter på at pågående kjøring er ferdig (inntil `timeout`)
4. Avslutter executor-tråden

---

### `fun Int.twoPad(): String`

Hjelpefunksjon som formaterer et heltall med ledende null (f.eks. `5` → `"05"`). Brukes i loggmeldinger.
