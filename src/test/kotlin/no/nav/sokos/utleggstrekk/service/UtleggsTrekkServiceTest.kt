package no.nav.sokos.utleggstrekk.service

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
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
import no.nav.sokos.utleggstrekk.util.resourceToString
import no.nav.sokos.utleggstrekk.utils.JavaLocaldateTimeSerializer
import no.nav.sokos.utleggstrekk.utils.LocalDateSerializer
import no.nav.sokos.utleggstrekk.utils.LocalDateTimeSerializer
import no.nav.sokos.utleggstrekk.utils.ZonedDateTimeSerializer

internal class UtleggsTrekkServiceTest :
    BehaviorSpec({

        Given("hentOgSendUtleggstrekk initieres") {

            val testContainer = TestContainer()
            testContainer.migrate()
            val skeClientMock = mockk<SkeClient>()
            val mqMock = mockk<MqProducer>(relaxed=true)
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
                UtleggsTrekkService(
                    databaseService,
                    BehandleTrekkService(databaseService),
                    skeClientMock,
                    mqMock
                )
            then("Først skal hentOgLagreNyeUtleggstrekk lagre alle i databasen") {
                coEvery { skeClientMock.hentAlleUtleggstrekk() } returns json.decodeFromString<List<Trekkpaalegg>>(resourceToString("FraSkatt_Trekkversjon1_1Trekkalternativ-2trekk.json"))
                utleggsTrekkService.hentOgLagreNyeUtleggstrekk()
                val trekkIdatabase = databaseService.hentAlleTrekkSomIkkeErSendt()
                trekkIdatabase.size shouldBe 2
                databaseService.hentAllePerioderForTrekkId(trekkIdatabase.first()).size shouldBe 3
                databaseService.hentAllePerioderForTrekkId(trekkIdatabase.last()).size shouldBe 1
            }
            then("Deretter skal trekk som er laget i behandleTrekkservice sendes og status skal oppdateres"){
                every {  mqMock.send(any()) } returns true
                val trekkIdatabase = databaseService.hentAlleTrekkSomIkkeErSendt()
                val trekkPairs = json.decodeFromString<List<Pair<UtleggstrekkTable, List<TrekkTilOppdrag>>>>(resourceToString("TrekkTilSendingPairMedRiktigCorrid.json"))
                val trekkSomSkalSendesMap = trekkPairs.mapIndexed { i, p -> p.first.copy(corrid = trekkIdatabase[i].corrid)  to p.second }.toMap()
                utleggsTrekkService.sendTrekkTilOS(trekkSomSkalSendesMap)
                trekkSomSkalSendesMap.forEach { println(it.key.corrid) }
                databaseService.hentAlleTrekkSomIkkeErSendt().size shouldBe 0
            }
        }

    })
