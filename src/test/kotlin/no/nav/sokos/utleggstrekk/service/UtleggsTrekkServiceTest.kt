package no.nav.sokos.utleggstrekk.service

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.Json
import no.nav.sokos.utleggstrekk.client.SkeClient
import no.nav.sokos.utleggstrekk.database.model.UtleggstrekkTable
import no.nav.sokos.utleggstrekk.domene.nav.TrekkTilOppdrag
import no.nav.sokos.utleggstrekk.domene.ske.Trekkpaalegg
import no.nav.sokos.utleggstrekk.mq.JmsProducerService
import no.nav.sokos.utleggstrekk.util.TestContainer
import no.nav.sokos.utleggstrekk.util.resourceToString

internal class UtleggsTrekkServiceTest :
    BehaviorSpec({

        val jsonConfig =
            Json {
                isLenient = true
                explicitNulls = false
                encodeDefaults = true
            }

        Given("hentOgSendUtleggstrekk initieres") {
            val databaseService = DatabaseService(TestContainer().dataSource)

            val utleggsTrekkService =
                UtleggsTrekkService(
                    databaseService = databaseService,
                    skeClient =
                        mockk<SkeClient> {
                            coEvery { hentAlleUtleggstrekk() } returns jsonConfig.decodeFromString<List<Trekkpaalegg>>(resourceToString("FraSkatt_Trekkversjon1_1Trekkalternativ-2trekk.json"))
                        },
                    mqProducer = mockk<JmsProducerService>(relaxed = true),
                )
            then("Først skal hentOgLagreNyeUtleggstrekk lagre alle i databasen") {
                utleggsTrekkService.hentOgLagreNyeUtleggstrekk()
                val trekkIdatabase = databaseService.hentAlleTrekkSomIkkeErSendt()
                trekkIdatabase.size shouldBe 2
                databaseService.hentAllePerioderForTrekkId(trekkIdatabase.first()).size shouldBe 3
                databaseService.hentAllePerioderForTrekkId(trekkIdatabase.last()).size shouldBe 1
            }

            then("Deretter skal trekk som er laget i behandleTrekkservice sendes og status skal oppdateres") {
                val trekkIdatabase = databaseService.hentAlleTrekkSomIkkeErSendt()
                val trekkPairs = jsonConfig.decodeFromString<List<Pair<UtleggstrekkTable, List<TrekkTilOppdrag>>>>(resourceToString("TrekkTilSendingPairMedRiktigCorrid.json"))

                trekkPairs.size shouldBe trekkIdatabase.size
                val trekkSomSkalSendesMap =
                    trekkPairs
                        .zip(trekkIdatabase)
                        .associate { (trekkPair, databaseRecord) ->
                            trekkPair.first.copy(corrid = databaseRecord.corrid) to trekkPair.second
                        }

                utleggsTrekkService.sendTrekkTilOS(trekkSomSkalSendesMap)
                databaseService.hentAlleTrekkSomIkkeErSendt().size shouldBe 0
            }
        }
    })
