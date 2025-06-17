package no.nav.sokos.utleggstrekk.service

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import no.nav.sokos.utleggstrekk.TestContainer
import no.nav.sokos.utleggstrekk.mq.MqConsumer
import no.nav.sokos.utleggstrekk.utils.JavaLocaldateTimeSerializer
import no.nav.sokos.utleggstrekk.utils.LocalDateSerializer
import no.nav.sokos.utleggstrekk.utils.LocalDateTimeSerializer
import no.nav.sokos.utleggstrekk.utils.ZonedDateTimeSerializer

class KvitteringServiceTest :
    BehaviorSpec({

        Given("Behandle kvitteringer initieres") {

            val testContainer = TestContainer()
            testContainer.migrate()
            val mqConsumerMock = mockk<MqConsumer>(relaxed = true)
            val databaseService = DatabaseService(testContainer.dataSource)
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

            val kvitteringservice =
                KvitteringService(
                    databaseService,
                    mqConsumerMock
                )
            then("Henterkvitteringer 1") {
                coEvery { mqConsumerMock.receive() } returns ""
                kvitteringservice.behandleKvitteringer()
            }
        }
    })
