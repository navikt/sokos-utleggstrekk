package no.nav.sokos.utleggstrekk.mq

import kotlin.time.Duration.Companion.seconds

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContainInOrder
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.apache.activemq.artemis.jms.client.ActiveMQQueue

import no.nav.sokos.utleggstrekk.database.model.INGEN_TREKK_ID_I_KVITTERING
import no.nav.sokos.utleggstrekk.database.model.KvitteringStatus
import no.nav.sokos.utleggstrekk.listener.DBListener
import no.nav.sokos.utleggstrekk.listener.DBListener.RepositoryNy
import no.nav.sokos.utleggstrekk.listener.MQListener
import no.nav.sokos.utleggstrekk.listener.MQListener.connectionFactory
import no.nav.sokos.utleggstrekk.service.SlackService
import no.nav.sokos.utleggstrekk.util.resourceToString

class JmsListenerServiceTest :
    BehaviorSpec({
        extensions(listOf(MQListener, DBListener))

        val slackService = mockk<SlackService>()
        every { slackService.addError(any(), any()) } returns Unit
        coEvery { slackService.sendErrors(any()) } returns Unit

        val replyQueue = ActiveMQQueue("replyQueue")

        JmsListenerService(
            RepositoryNy,
            slackService,
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
            val transaksjonerBefore = RepositoryNy.getAllTransaksjonerTilOs()
            transaksjonerBefore.size shouldBe 1

            val transaksjon = transaksjonerBefore.first()
            transaksjon.kvitteringStatus shouldBe KvitteringStatus.IKKE_MOTTATT

            When("OK Kvittering prosesseres") {
                val kvittering = resourceToString("mq/trekk_med_kvittering_ok/trekk1_ok_kvittering.json")
                jmsProducerTrekk.send(kvittering)
                Then("Skal trekk oppdateres med status ${KvitteringStatus.OK}") {
                    eventually(duration = 1.seconds) {
                        val transaksjonerAfter = RepositoryNy.getTransaksjonTilOs(transaksjon.transaksjonsID)
                        transaksjonerAfter.shouldNotBeNull()
                        transaksjonerAfter.kvitteringStatus shouldBe KvitteringStatus.OK
                        transaksjonerAfter.navTrekkId shouldBe "navTrekkId01"

                        coVerify(exactly = 1) { slackService.sendErrors("Kvittering fra oppdrag feil") }
                    }
                }
            }

            When("Ikke OK Kvittering prosesseres") {

                val kvittering = resourceToString("mq/trekk_med_kvittering_ikke_ok/trekk1_ikke_ok_kvittering.json")
                jmsProducerTrekk.send(kvittering)

                Then("Skal trekk oppdateres med status ${KvitteringStatus.FEIL}") {
                    eventually(duration = 1.seconds) {
                        val trekkAfter = RepositoryNy.getTransaksjonTilOs(transaksjon.transaksjonsID)
                        trekkAfter.shouldNotBeNull()
                        trekkAfter.kvitteringStatus shouldBe KvitteringStatus.FEIL
                        trekkAfter.navTrekkId shouldBe INGEN_TREKK_ID_I_KVITTERING
                    }
                }
                Then("Feil skal insertes i database") {
                    eventually(duration = 1.seconds) {
                        val feilmelding = RepositoryNy.getFeilmeldingerFraOS(transaksjon.transaksjonsID)
                        feilmelding.shouldNotBeNull()
                        feilmelding.feilkode shouldBe "B7XX001F"
                        feilmelding.beskrivelse shouldBe "Ugyldig verdi i felt: Trekktype"
                    }
                }
                Then("Feil skal sendes til slack") {
                    val message = slot<String>()
                    eventually(duration = 1.seconds) {
                        coVerify(exactly = 1) {
                            slackService.addError("Kvittering feil", capture(message))
                            slackService.sendErrors("Kvittering fra oppdrag feil")
                        }
                        message.captured.shouldContainInOrder(
                            "Trekk med kreditorstrekkID: 10342395",
                            "corrid: TransaksjonsId01",
                            "feilkode: B7XX001F",
                            "beskrivelse: Ugyldig verdi i felt: Trekktype",
                        )
                    }
                }
            }

            When("Kvittering med manglende data prosesseres") {
                val kvittering = resourceToString("mq/kvittering_uten_transaksjonsID.json")
                jmsProducerTrekk.send(kvittering)
                Then("Feil skal sendes til slack") {
                    eventually(duration = 1.seconds) {
                        coVerify(exactly = 1) {
                            slackService.addError("Prosessering av kvitteringmelding feilet.", any())
                            slackService.sendErrors("Kvittering fra oppdrag feil")
                        }
                    }
                }
            }
        }

        afterContainer {
            clearMocks(slackService, answers = false)
        }
    })