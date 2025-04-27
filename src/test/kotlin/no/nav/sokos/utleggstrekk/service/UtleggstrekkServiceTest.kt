package no.nav.sokos.utleggstrekk.service

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import no.nav.sokos.utleggstrekk.TestContainer
import no.nav.sokos.utleggstrekk.client.SkeClient
import no.nav.sokos.utleggstrekk.database.model.UtleggstrekkTable
import no.nav.sokos.utleggstrekk.domene.nav.TrekkTilOppdrag
import no.nav.sokos.utleggstrekk.domene.ske.Trekkpaalegg
import no.nav.sokos.utleggstrekk.mq.MqProducer
import no.nav.sokos.utleggstrekk.util.MockHttpClient
import no.nav.sokos.utleggstrekk.util.Responses
import no.nav.sokos.utleggstrekk.utils.JavaLocaldateTimeSerializer
import no.nav.sokos.utleggstrekk.utils.LocalDateSerializer
import no.nav.sokos.utleggstrekk.utils.LocalDateTimeSerializer
import no.nav.sokos.utleggstrekk.utils.ZonedDateTimeSerializer

internal class UtleggstrekkServiceTest :
    BehaviorSpec({

        Given("hentOgSendUtleggstrekk initieres") {

            val testContainer = TestContainer()
            testContainer.migrate()
            val mockClient = MockHttpClient().getClient(Responses.utleggsTrekkListeFraSkatt, HttpStatusCode.OK)
            val skeClientMock = mockk<SkeClient>()
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

            val utleggsTrekkService =
                UtleggstrekkService(
                    databaseService,
                    BehandleTrekkService(databaseService),
                    skeClientMock,
                    mockk<MqProducer>(relaxed = true),
                )
            then("Først skal hentOgLagreNyeUtleggstrekk lagre alle i databasen") {
                coEvery { skeClientMock.hentAlleUtleggstrekk() } returns json.decodeFromString<List<Trekkpaalegg>>(Responses.utleggsTrekkListeFraSkatt)
                utleggsTrekkService.hentOgLagreNyeUtleggstrekk()
                val trekkIdatabase = databaseService.hentAlleTrekkSomIkkeErSendt()
                trekkIdatabase.size shouldBe 2
            }
            then("Deretter skal trekk som lages i behandleTrekkservice sendes"){
                val trekkSomSkalSendes = json.decodeFromString<List<Pair<UtleggstrekkTable, List<TrekkTilOppdrag>>>>(Responses.trekkSomSkalSendes)

            }
        }

    })
