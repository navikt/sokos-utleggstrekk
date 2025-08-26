package no.nav.sokos.utleggstrekk.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import no.nav.sokos.utleggstrekk.TestContainer
import no.nav.sokos.utleggstrekk.database.Repository
import no.nav.sokos.utleggstrekk.database.TestRepositoryExtensions.fetchAllUtleggstrekk
import no.nav.sokos.utleggstrekk.domene.nav.TrekkTilOppdrag
import no.nav.sokos.utleggstrekk.mq.MqConsumer
import no.nav.sokos.utleggstrekk.util.resourceToString
import no.nav.sokos.utleggstrekk.util.resourceToStringList
import no.nav.sokos.utleggstrekk.utils.JavaLocaldateTimeSerializer
import no.nav.sokos.utleggstrekk.utils.LocalDateSerializer
import no.nav.sokos.utleggstrekk.utils.LocalDateTimeSerializer
import no.nav.sokos.utleggstrekk.utils.SQLUtils.withTransaction
import no.nav.sokos.utleggstrekk.utils.ZonedDateTimeSerializer

class KvitteringServiceTest :
    FunSpec({
        val testContainer = TestContainer()
        testContainer.migrate()
        val databaseService = DatabaseService(testContainer.dataSource)
        val repository = Repository(testContainer.dataSource)
        val mqConsumerMock = mockk<MqConsumer>(relaxed = true)
        val kvitteringservice =
            KvitteringService(
                databaseService,
                mqConsumerMock
            )

        val json = Json {
            prettyPrint = true
            isLenient = true
            explicitNulls = false
            serializersModule = SerializersModule {
                contextual(JavaLocaldateTimeSerializer)
                contextual(LocalDateTimeSerializer)
                contextual(LocalDateSerializer)
                contextual(ZonedDateTimeSerializer)
            }
        }

        test("Det er ingen meldinger på kø") {
            clearMocks(mqConsumerMock)
            coEvery { mqConsumerMock.receive() } returns null
            println(kvitteringservice.hentAlleKvitteringer())
            coVerify(exactly = 1) { mqConsumerMock.receive() }
        }
        test("det er 1 melding på kø så det leses 2 ganger fra kø") {
            clearMocks(mqConsumerMock)
            val kvittFraMq: String? = resourceToString("Kvitering-ok.json")
            coEvery { mqConsumerMock.receive() } returns kvittFraMq andThen null
            println(kvitteringservice.hentAlleKvitteringer())
            coVerify(exactly = 2) { mqConsumerMock.receive() }
        }
        test("det er 30 melding på kø så det leses 31 ganger fra kø") {
            clearMocks(mqConsumerMock)
            val kvittFraMq = resourceToStringList("DiverseTestDokumenterKvitteringer_fraOs.txt")
            coEvery { mqConsumerMock.receive() } returnsMany kvittFraMq andThen null
            kvitteringservice.hentAlleKvitteringer()
            coVerify(exactly = 31) { mqConsumerMock.receive() }
        }
        test("Med 30 meldinger på kø skal all ekodemelding ende i riktig felt i databasen") {
            clearMocks(mqConsumerMock)
            val kvittFraMq = resourceToStringList("DiverseTestDokumenterKvitteringer_fraOs.txt")
            testContainer.migrate("utleggstrekk.sql")

            coEvery { mqConsumerMock.receive() } returnsMany kvittFraMq andThen null
            kvitteringservice.behandleKvitteringer()
            coVerify(exactly = 31) { mqConsumerMock.receive() }

            val mqListe = kvittFraMq.map { json.decodeFromString<TrekkTilOppdrag>(it) }

            val dbliste = testContainer.dataSource.withTransaction { session-> repository.fetchAllUtleggstrekk(session) }

            mqListe.forEach { mqDoc ->
                when (mqDoc.dokument.innrapporteringTrekk.kodeTrekkAlternativ) {
                    "LOPM" -> dbliste.first { it.corrid == mqDoc.dokument.transaksjonsId }.kvitteringLOPM shouldBe mqDoc.mmel!!.kodeMelding
                    else -> dbliste.first { it.corrid == mqDoc.dokument.transaksjonsId }.kvitteringLOPP shouldBe mqDoc.mmel!!.kodeMelding
                }

            }
        }
    })

