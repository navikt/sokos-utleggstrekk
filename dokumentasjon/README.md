# sokos-utleggstrekk
Deployes i GPC.

## Hva gjør sokos-utleggstrekk
Hver time trigges UtleggsTrekkService.schedule() på minuttet konfigurert i __SCEDULER_MINUTES__ og utfører følgende ting:

1. [Den henter nye __trekkpålegg__ fra Skatteetaten fra siste kjente sekvensnummer og lagrer disse i databasen](sekvensdiagrammer.md#henting-av-trekk-fra-skattetaten)
2. [Sammenlikner med tidligere __Innrapporteringtrekk__ og genererer **NY**e eller **ENDR**ede __Innrapporteringtrekk__](sekvensdiagrammer.md#behandling-av-trekk-for-å-lage-meldinger-til-oppdrag-z-). 
   Genererer meldinger for sending, og lagrer disse i databasen.  
3. [Sender innrapporteringstrekk til Oppdrag Z over MQ](sekvensdiagrammer.md#sending-av-meldinginger-til-oppdrag-z)
4. Mottar kvitteringer fra Oppdrag Z og lagrer **nav_trekk_id** eller feilmeldinger i databasen. 
5. Sletter gamle data. Gamle data er definert som data som hører til et trekk som ble avsluttet for mer enn 6 måneder siden.

## Konfigurasjon
#### Applikasjon

|                             Property |            Default |                                                                        Forklaring |       Kilde |
|-------------------------------------:|-------------------:|----------------------------------------------------------------------------------:|------------:|
|                        NAIS_APP_NAME | sokos-utleggstrekk |                                                           Navnet på applikasjonen |        NAIS |
|                       NAIS_NAMESPACE |            okonomi |                                                               Navnet på namespace |        NAIS |
|              AZURE_APP_PROVIDER_NAME |            azureAd |                                                               Autentiseringskilde |             |
|                     SCEDULER_MINUTES |                 55 | Regulerer hvilket minutt på timen jobben starter med å hente data fra Skattetaten |             |
|                   USE_AUTHENTICATION |              false |                          Skrur av og på autentisering på sokos-utleggstrekk APIet |             |
| SOKOS_UTLEGGSTREKK_SLACK_WEBHOOK_URL |                    |                                             Webhook for å sende alarmer til Slack | NAIS secret |

#### Databaseoppkobling

|          Property |              Default |                                        Forklaring | Kilde |
|------------------:|---------------------:|--------------------------------------------------:|------:|
|     POSTGRES_HOST | dev-pg.intern.nav.no |                                       DB Hostname |       |
|     POSTGRES_PORT |                 5432 |                                           DB Port |       |
|     POSTGRES_NAME |   sokos-utleggstrekk |                                  Navn på database |       |
| POSTGRES_USERNAME |   sokos-utleggstrekk | Navn på databasebruker. Brukes bare til utvikling |       |
| POSTGRES_PASSWORD |                      |        Databasepassord. Brukes bare til utvikling |       |
| POSTGRES_JDBC_URL |                      |                JDBC Url med brukernavn og passord |  NAIS |

#### MQ

|              Property |                                 Default |                                                           Forklaring |           Kilde |
|----------------------:|----------------------------------------:|---------------------------------------------------------------------:|----------------:|  
|           MQ_HOSTNAME |                    mqls02.preprod.local |                                                             Hostname | naiserator.yaml |
|               MQ_PORT |                                         |                                                                      | naiserator.yaml |
|            MQ_CHANNEL |                         Q1_UTLEGGSTREKK |                                                              Channel | naiserator.yaml |
|         MQ_QUEUE_NAME |      QA.Q1_231.OB04_TREKK_FRASKATT_JSON | Kø brukt av sokos-utleggstrekk til å sende trekk til Oppdragsystemet | naiserator.yaml |
|    MQ_REPLYQUEUE_NAME |     QA.Q1_SOKOS_UTLEGGSTREKK.KVITTERING |    Kø for Oppdragsystemets kvitteringer sendt til sokos-utleggstrekk | naiserator.yaml |
|           MQ_USERNAME |                                         |                                                        MQ brukernavn |     NAIS Secret |
|           MQ_PASSWORD |                                         |                                                           MQ passord |     NAIS Secret |
| MQ_QUEUE_MANAGER_NAME |                                  MQLS02 |                                                                      | naiserator.yaml |

#### Skatteetaten
|     Property |                                      Default |                                                            Forklaring |       Kilde |
|-------------:|---------------------------------------------:|----------------------------------------------------------------------:|------------:|  
| SKE_REST_URL | https://api-test.sits.no/api/trekkpaalegg/v1 |                          Endepunkt for å hente trekk fra Skatteetaten | NAIS Secret |
|    SKE_ORGNR |                                    971648198 |                Skatteetatens orgnummer. Brukes til å mappe til TSS ID | NAIS Secret |
|    SKE_TTSID |                                              | TTS id. Brukes til å identifisere Skatteetaten i trekkene til Oppdrag | NAIS Secret |
|  SKE_KONTONR |                                              |                  Skatteetatens kontonummer trekket skal utbetales til | NAIS Secret |


## Alarmer
Sendes til Slack. Se __SOKOS_UTLEGGSTREKK_SLACK_WEBHOOK_URL__.


## Unleash
Det er tre feature toggles i [Unleash](https://okonomi-unleash-web.iap.nav.cloud.nais.io/)

* sokos-utleggstrekk.hent-fra-ske.enabled
* sokos-utleggstrekk.prosesser-utleggstrekk.enabled
* sokos-utleggstrekk.send-til-os.enabled

## [Datamodell](datamodell/README.md)
