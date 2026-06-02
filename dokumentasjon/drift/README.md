# Driftshåndbok

## Feilsituasjoner og korrigering
Det finnes ikke noe brukergrensesnitt i sokos-utleggstrekk for fagarbeidere til å gjøre endringer. Det er først mulig etter at trekk har blitt overført til Oppgrag Z, men merk
at sokos-utleggstrekk betrakter seg selv som fasit for trekkene den sender til Oppdrag, så hvis det gjøres endringer på disse vil de bli overskrevet neste 
gang det kommer en ny trekkversjon inn.

### Dersom trekk fra skatt har blitt avvist av sokos-utleggstrekk
Trekk vil bli avvist dersom sokos-utleggstrekk ikke klarer å tolke responsen fra Skatt. Dette betyr typisk at vi ikke har klart å parse responsen fra skatt 
som JSON. I denne feilsituasjonen har ingenting blitt lagret i sokos-utleggstrekk. Det betyr at siste sekvensnummer ikke vil oppdatere seg, og 
sokos-utleggstrekk vil forsøke å hente de samme datene igjen neste gang jobben kjøres

Trekk vil også bli avvist dersom de inneholder ugydlige verdier, 

Dette kan være pga. format-endringer eller at responsen inneholder felter med ugylding innhold eller ulovlige tegn. I denne feilsituasjonen har ingenting blitt lagret i sokos-utleggstrekk. Det betyr at siste sekvensnummer 
ikke vil oppdatere seg, og sokos-utleggstrekk vil forsøke å hente de samme datene igjen neste gang jobben kjøres. I denne situasjonen må enten trekket korrigeres
av Skatteetaten, ellers må sokos-utleggstrekk fikses og deployes av oss.

Trekk kan også bli avvist dersom det ikke passerer validering (ugyldige tegn), eller førte til en feil i behandling av trekket. I denne situasjonen vil man
finne en rad i tabellen `fraskatt_status` med status `AVVIST`. Fordi trekket ble hentet vil siste sekvensnummer også endre seg, så det vil ikke bli forsøkt
hentet igjen, men ingenting har blitt sendt til Oppdrag Z. Dette kan korrigeres ved at en eventuell feil med trekket rettes opp av Skatteetaten slik at
vi får en ny versjon av trekket, eller hvis feilen er på sokos-utleggstrekks side kan man manuelt endre `fraskatt_status.status` til `REPETERES`. Da
vil dette trekket bli forsøkt behandlet på nytt, med mindre det har kommet en ny versjon av trekket. Da vil status settes til `HOPPET OVER`. 
Det finnes en [oversikt over tilstandene](../flytdiagram/README.md) til et trekk.

### Dersom trekk har blitt mottatt, blitt behandlet av sokos-utleggstrekk men finnes ikke i Oppdrag Z.
I denne situasjonen vil `fraskatt_status.status` inneholde verdien `BEHANDLET`, som betyr at det er laget et innslag i tabellen
`transaksjon_til_os`. Denne raden inneholder mange kolonner, men merk at de faktiske dataene som skal sendes til OS ligger i kollonnen `dokument_json`
Her vil det hjelpe å se på feltene `kvittering_status` og `transaksjon_status`.

Dersom `transaksjon_status` er `IKKE_SENDT` har ikke meldingen blitt forsøkt send, og vil bli sendt neste gang jobben kjøres. Er den `VALIDERINGSFEIL`
betyr det at det finnes en feil i sokos-utleggstrekk som har produsert en `dokument_json` som ikke passerer valideringskontrollen av utdata. 
Er den `SENDT` se på `KVITTERING_STATUS`. 

Er `KVITTERING_STATUS` = `OK` har sokos-utleggsrekk motatt bekreftelse på at trekket er akseptert av Oppdrag Z. Andre mulige verdier på `KVITTERING_STATUS` er 
`IKKE_MOTTATT`, `FEIL` og `UKJENT`. I de to siste tilfellene skal det være innslag i tabellen `feilmelding` med mer ufyllende beskrivelse.

Det vil i denne feilsituasjonen også være mulig å endre `fraskatt_status.status` til `REPETERES`. Det vil føre til at det under neste kjøring vil bli laget en ny rad
i `transaksjon_til_os` som så blir sendt over til Oppdrag Z.

### Gjenoppbygging etter tap av data
I en situasjon hvor man har mistet alle data er det mulig å hente den ut igjen fra Skatteetatens API ved å spørre fra sekvensnummer=0. Merk da at dette kallet bare vil
returnere det som er gjeldende versjoner av hvert trekk, ikke tidligere versjoner. For å gjenskape korrekt tilstand er man derfor nødt til å enten hente tilstand fra Oppdrag
slik at korrekt diff kan beregnes, eller slette tilstand i Oppdrag Z slik at alle de siste trekkversjoner blir til nye trekk i Oppdragsystemet.

Det er mulig å hente eldre versjoner fra Skattetaten, men dette kan bare gjøres enkeltvis.

Sokos-utleggstrekk betrakter seg selv som fasit for trekkene den sender til Oppdrag, så hvis det gjøres endringer på disse vil de bli overskrevet neste
gang det kommer en ny trekkversjon inn. 