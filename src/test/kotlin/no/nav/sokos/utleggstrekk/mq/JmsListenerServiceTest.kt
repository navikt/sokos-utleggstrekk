package no.nav.sokos.utleggstrekk.mq

import kotlinx.serialization.json.Json

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.spyk
import io.mockk.verify
import kotliquery.Session
import kotliquery.queryOf
import org.apache.activemq.artemis.jms.client.ActiveMQQueue

import no.nav.sokos.utleggstrekk.database.model.UtleggstrekkStatus
import no.nav.sokos.utleggstrekk.database.model.UtleggstrekkTable
import no.nav.sokos.utleggstrekk.domene.nav.TrekkTilOppdrag
import no.nav.sokos.utleggstrekk.listener.MQListener
import no.nav.sokos.utleggstrekk.listener.MQListener.connectionFactory
import no.nav.sokos.utleggstrekk.service.DatabaseService
import no.nav.sokos.utleggstrekk.util.TestContainer
import no.nav.sokos.utleggstrekk.util.resourceToString
import no.nav.sokos.utleggstrekk.utils.SQLUtils.withTransaction

class KvitteringTest :
    BehaviorSpec({
        extensions(listOf(MQListener))

        val testContainer = TestContainer()
        val dbService = DatabaseService(testContainer.dataSource)
        val replyQueue = ActiveMQQueue("replyQueue")
        val dbServiceSpy = spyk(dbService)

        JmsListenerService(
            databaseService = dbServiceSpy,
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

        val jsonConfig =
            Json {
                isLenient = true
                explicitNulls = false
                encodeDefaults = true
            }

        Given("Vi mottar en OK kvittering") {
            testContainer.loadInitScript("mq/trekk_med_kvittering_ok/init_db.sql")
            val trekkBefore = testContainer.dataSource.withTransaction { session -> fetchTrekkWithCorrId(session, "CorrId01") }.first()
            trekkBefore.status shouldBe UtleggstrekkStatus.MOTTATT.status
            trekkBefore.kvitteringLOPM shouldBe null
            trekkBefore.kvitteringLOPP shouldBe null

            val kvittering = resourceToString("mq/trekk_med_kvittering_ok/trekk1_ok_kvittering.json")
            jmsProducerTrekk.send(kvittering)

            When("OK Kvittering prosesseres") {
                verify(timeout = 1000) { dbServiceSpy.oppdaterTrekkMedKvitteringsinfo(jsonConfig.decodeFromString<TrekkTilOppdrag>(kvittering)) }
                Then("Skal trekk oppdateres med status ${UtleggstrekkStatus.KVITTERING_OK}") {
                    val trekkAfter = testContainer.dataSource.withTransaction { session -> fetchTrekkWithCorrId(session, "CorrId01") }.first()
                    trekkAfter.status shouldBe UtleggstrekkStatus.KVITTERING_OK.status
                    trekkAfter.kvitteringLOPM shouldBe "B782008I"
                    trekkAfter.kvitteringLOPP shouldBe null
                }
            }
        }

        Given("Vi mottar en ikke-ok kvittering") {
            testContainer.loadInitScript("mq/trekk_med_kvittering_ikke_ok/init_db.sql")
            val trekkBefore = testContainer.dataSource.withTransaction { session -> fetchTrekkWithCorrId(session, "CorrId02") }.first()

            trekkBefore.status shouldBe UtleggstrekkStatus.MOTTATT.status
            trekkBefore.kvitteringLOPM shouldBe null
            trekkBefore.kvitteringLOPP shouldBe null

            When("Ikke OK kvittering prosesseres") {
                val kvittering = resourceToString("mq/trekk_med_kvittering_ikke_ok/trekk1_ikke_ok_kvittering.json")
                jmsProducerTrekk.send(kvittering)
                verify(timeout = 1000) { dbServiceSpy.oppdaterTrekkMedKvitteringsinfo(jsonConfig.decodeFromString<TrekkTilOppdrag>(kvittering)) }

                Then("Skal trekk oppdateres med status ${UtleggstrekkStatus.KVITTERING_FEILET}") {
                    val trekkAfter = testContainer.dataSource.withTransaction { session -> fetchTrekkWithCorrId(session, "CorrId01") }.first()
                    trekkAfter.status shouldBe UtleggstrekkStatus.KVITTERING_FEILET.status
                    trekkAfter.kvitteringLOPM shouldBe "B7XX001F"
                    trekkAfter.kvitteringLOPP shouldBe null
                }
            }
        }
    })

fun fetchTrekkWithCorrId(
    session: Session,
    corrid: String,
): List<UtleggstrekkTable> =
    session.list(
        queryOf(
            """select * from utleggstrekk where corr_id = :corrId""",
            mapOf(
                "corrId" to corrid,
            ),
        ),
    ) { row -> UtleggstrekkTable(row) }
