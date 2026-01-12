
# Trekkpålegg
Dette er datamodellen for Trekkpålegg fra Skatteetaten.
Hver rad i fraskatt-tabellen er en trekk*versjon*. Kun den siste trekkversjonen er den gyldige. Skatteetatens APIer returnerer kun siste versjonen
av et trekk. Fordi Oppdrag Zs API opererer med endringer på trekk tar vi vare på tidligere versjoner for å kunne beregne differansen.

```mermaid
---
Fra skatt
---
erDiagram
    fraskatt ||--|| fraskatt_status: har 
    fraskatt {
        bigint id "Syntetisk primærnøkkel"
        text trekkid "Identifiserer et trekk (UUID v4)"
        int sekvensnummer "Økende nummer som brukes til å spørre etter nyere trekk"
        smallint trekkversjon "Versjonsnummer på trekk"
        text opprettet "Dato og tidspunkt for opprettelse av denne trekkversjonen"
        text saksnummer "Identifiserer saken hvor trekkpålegget ble besluttet"        
        text trekkpliktig "Organisasjonsnummer"
        text skyldner "f.nr eller d.nr"
        text trekkstatus "aktiv eller avsluttet"
        timestampz tidspunkt_opprettet "Tidspunkt denne raden ble oppdatert"        
    }
    fraskatt_status {
        bigint id "Syntetisk primærnøkkel"
        bigint fraskatt_id "Fremmednøkkel til fraskatt"
        text status "mottatt eller behandlet"
        timestampz tidspunkt_satt "Tidspunkt for status"
    }
    fraskatt ||--o{ periode: har
    periode {
        bigint id "Syntetisk primærnøkkel"
        bigint fraskatt_id "Fremmednøkkel til fraskatt"
        text trekk_id_ske "Identifiserer et trekk"
        text dato_start "Dato for når trekket starter"
        text dato_slutt "Dato for når trekket opphører"
        decimal trekkbelop "Trekkbeløp" 
        decimal trekkprosent "Trekkprosent" 
    }
    fraskatt ||--|| betalingsinformasjonfraskatt: har
    betalingsinformasjonfraskatt {
        bigint id "Syntetisk primærnøkkel"
        bigint fraskatt_id "Fremmednøkkel til fraskatt"
        text betalingsmottaker "Organisasjonsnummer"
        text kidnummer "Kidnummer"
        text kontonummer "Kontonummer"
    }
```