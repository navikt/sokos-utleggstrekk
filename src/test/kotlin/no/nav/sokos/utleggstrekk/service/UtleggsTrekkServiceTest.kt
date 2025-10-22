package no.nav.sokos.utleggstrekk.service

import kotlinx.serialization.json.Json

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk

import no.nav.sokos.utleggstrekk.client.SkeClient
import no.nav.sokos.utleggstrekk.database.model.UtleggstrekkTable
import no.nav.sokos.utleggstrekk.domene.nav.TrekkTilOppdrag
import no.nav.sokos.utleggstrekk.domene.ske.Trekkpaalegg
import no.nav.sokos.utleggstrekk.mq.JmsProducerService
import no.nav.sokos.utleggstrekk.util.TestContainer
import no.nav.sokos.utleggstrekk.util.resourceToString

internal class UtleggsTrekkServiceTest :
    BehaviorSpec({
        // TODO: Bytte til global json config
        val jsonConfig =
            Json {
                isLenient = true
                explicitNulls = false
                encodeDefaults = true
            }

        // TODO: refaktorer
        Given("hentOgSendUtleggstrekk initieres") {
            val databaseService = DatabaseService(TestContainer().dataSource)
            val capturedPayloads = mutableListOf<String>()

            val mqProducerMock =
                mockk<JmsProducerService>(relaxed = true) {
                    every { send(capture(capturedPayloads)) } just Runs
                }

            val utleggsTrekkService =
                UtleggsTrekkService(
                    databaseService = databaseService,
                    skeClient =
                        mockk<SkeClient> {
                            coEvery { hentAlleUtleggstrekk() } returns jsonConfig.decodeFromString<List<Trekkpaalegg>>(resourceToString("FraSkatt_Trekkversjon1_1Trekkalternativ-2trekk.json"))
                        },
                    mqProducer = mqProducerMock,
                )
            then("Først skal hentOgLagreNyeUtleggstrekk lagre alle i databasen") {
                utleggsTrekkService.hentOgLagreNyeUtleggstrekk()
                val trekkIdatabase = databaseService.hentAlleTrekkSomIkkeErSendt()
                trekkIdatabase.size shouldBe 2
                databaseService.hentAllePerioderForTrekkId(trekkIdatabase.first()).size shouldBe 3
                databaseService.hentAllePerioderForTrekkId(trekkIdatabase.last()).size shouldBe 1
            }

            then("Deretter skal trekk som er laget i behandleTrekkservice sendes") {
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

                capturedPayloads.forEach {
                    it shouldContain "SOKOSUTLEGG"
                    it shouldContain "TRK1"
                }
            }

            then("skal status oppdateres til SENDT") {
                databaseService.hentAlleTrekkSomIkkeErSendt().size shouldBe 0
            }
        }
    })
