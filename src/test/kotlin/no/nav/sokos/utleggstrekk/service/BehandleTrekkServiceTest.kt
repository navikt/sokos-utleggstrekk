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
import no.nav.sokos.utleggstrekk.util.FileUtil
import no.nav.sokos.utleggstrekk.util.MockHttpClient
import no.nav.sokos.utleggstrekk.util.Responses
import no.nav.sokos.utleggstrekk.utils.JavaLocaldateTimeSerializer
import no.nav.sokos.utleggstrekk.utils.LocalDateSerializer
import no.nav.sokos.utleggstrekk.utils.LocalDateTimeSerializer
import no.nav.sokos.utleggstrekk.utils.ZonedDateTimeSerializer
import java.io.File

class BehandleTrekkServiceTest :
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
            then("Behandle nytt trekk med perioder kun med 1 trekkalternativ i trekkversjon 1") {
                coEvery { skeClientMock.hentAlleUtleggstrekk() } returns json.decodeFromString<List<Trekkpaalegg>>(Responses.utleggsTrekkListeFraSkatt)
                utleggsTrekkService.hentOgLagreNyeUtleggstrekk()
                val trekkIdatabase = databaseService.hentAlleTrekkSomIkkeErSendt()
                trekkIdatabase.size shouldBe 2
            }
            then("behandle nytt trekk med perioder kun med 2 trekkalternativ  i trekkversjon 1") {
                val trekkSomSkalSendes = json.decodeFromString<List<Pair<UtleggstrekkTable, List<TrekkTilOppdrag>>>>(Responses.trekkSomSkalSendes)

            }
            then("tester kvittering") {
                val kvitteringsliste = FileUtil.fileAsList("${File.separator}div_testdokumenter_fra_os.json")
                var i = 0;
                val kvitteringsMap = kvitteringsliste.map { msg ->
                    i += 1
                    Json.decodeFromString<TrekkTilOppdrag>(msg).also { println(it) }
                }.groupBy { it.mmel!!.kodeMelding }
                println(kvitteringsMap.size)
                kvitteringsMap.forEach {
                    println("\n${it.key}")
                    it.value.forEach { println("\t${it.mmel!!.alvorlighetsgrad}, ${it.mmel!!.beskrMelding}") }
                }
            }
        }

    })
