package no.nav.sokos.utleggstrekk.mq

import kotlin.time.Duration.Companion.seconds

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.apache.activemq.artemis.jms.client.ActiveMQQueue

import no.nav.sokos.utleggstrekk.database.RepositoryNy
import no.nav.sokos.utleggstrekk.database.RepositoryNy.getAllTransaksjonerTilOs
import no.nav.sokos.utleggstrekk.database.RepositoryNy.getTransaksjonTilOs
import no.nav.sokos.utleggstrekk.database.model.KvitteringStatus
import no.nav.sokos.utleggstrekk.listener.DBListener
import no.nav.sokos.utleggstrekk.listener.MQListener
import no.nav.sokos.utleggstrekk.listener.MQListener.connectionFactory
import no.nav.sokos.utleggstrekk.service.withTransaction
import no.nav.sokos.utleggstrekk.util.resourceToString

class JmsListenerServiceTest :
    BehaviorSpec({
        extensions(listOf(MQListener, DBListener))

        val replyQueue = ActiveMQQueue("replyQueue")

        JmsListenerService(
            DBListener.dataSource,
            osKvitteringQueue = replyQueue,
            connectionFactory,
        )

        val jmsProducerTrekk: JmsProducerService by lazy {
            JmsProducerService(
                targetQueue = replyQueue,
                replyQueue = replyQueue,
                connectionFactory,
            )
        }

        Given("Vi mottar en kvittering") {
            DBListener.loadInitScript("mq/trekk_med_kvittering_ok/init_db.sql")
            val transaksjonerBefore = DBListener.dataSource.withTransaction { session -> getAllTransaksjonerTilOs(session) }
            transaksjonerBefore.size shouldBe 1

            val transaksjon = transaksjonerBefore.first()
            transaksjon.kvitteringStatus shouldBe KvitteringStatus.IKKE_MOTTATT

            When("OK Kvittering prosesseres") {
                val kvittering = resourceToString("mq/trekk_med_kvittering_ok/trekk1_ok_kvittering.json")
                jmsProducerTrekk.send(kvittering)
                Then("Skal trekk oppdateres med status ${KvitteringStatus.OK}") {
                    eventually(duration = 1.seconds) {
                        val transaksjonerAfter = DBListener.dataSource.withTransaction { session -> getTransaksjonTilOs(transaksjon.transaksjonsID, session) }
                        transaksjonerAfter.shouldNotBeNull()
                        transaksjonerAfter.kvitteringStatus shouldBe KvitteringStatus.OK
                        // TODO: trransaksjon må oppdateres med navtrekkid (som vi får fra kvitteringen)
                        transaksjonerAfter.navTrekkId shouldBe "navTrekkId01"
                    }
                }
            }

            When("Ikke OK Kvittering prosesseres") {

                val kvittering = resourceToString("mq/trekk_med_kvittering_ikke_ok/trekk1_ikke_ok_kvittering.json")
                jmsProducerTrekk.send(kvittering)

                Then("Skal trekk oppdateres med status ${KvitteringStatus.FEIL}") {
                    eventually(duration = 1.seconds) {
                        val trekkAfter =
                            DBListener.dataSource.withTransaction { session ->
                                getTransaksjonTilOs(transaksjon.transaksjonsID, session)
                            }
                        trekkAfter.shouldNotBeNull()
                        trekkAfter.kvitteringStatus shouldBe KvitteringStatus.FEIL
                    }
                }
                And("Feil skal insertes i database") {
                    eventually(duration = 1.seconds) {
                        val feilmelding = DBListener.dataSource.withTransaction { session -> RepositoryNy.getFeilmeldingerFraOS(transaksjon.transaksjonsID, session) }
                        feilmelding.shouldNotBeNull()
                        feilmelding.feilkode shouldBe "B7XX001F"
                        feilmelding.beskrivelse shouldBe "Ugyldig verdi i felt: Trekktype"
                    }
                }
            }
        }
    })
