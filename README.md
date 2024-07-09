# sokos-utleggstrekk

## Applikasjon for å få inn  utleggstrekk fra skatteetaten

### Input 


- Kjør scriptet [setupLocalEnvironment.sh](setupLocalEnvironment.sh)
     ```
     chmod 755 setupLocalEnvironment.sh && ./setupLocalEnvironment.sh
     ```                                
  Denne vil opprette [default.properties](defaults.properties) med alle environment variabler (bortsett fra
  POSTGRES_USERNAME og POSTGRES_PASSWORD, som må hentes manuelt fra vault) du trenger for å kjøre
  applikasjonen som er definert i [PropertiesConfig](src/main/kotlin/sokos/ske/krav/config/PropertiesConfig.kt).
