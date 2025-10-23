@file:OptIn(ExperimentalTime::class)

package no.nav.sokos.utleggstrekk.service

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.just
import io.mockk.mockk

import no.nav.sokos.utleggstrekk.database.model.TrekkPeriodeTable
import no.nav.sokos.utleggstrekk.database.model.UtleggstrekkStatus.MOTTATT
import no.nav.sokos.utleggstrekk.database.model.UtleggstrekkTable
import no.nav.sokos.utleggstrekk.domene.nav.Aksjonskode
import no.nav.sokos.utleggstrekk.domene.nav.TrekkAlternativ.LOPM
import no.nav.sokos.utleggstrekk.domene.nav.TrekkAlternativ.LOPP
import no.nav.sokos.utleggstrekk.domene.nav.TrekkTilOppdrag
import no.nav.sokos.utleggstrekk.domene.ske.Trekkstatus
import no.nav.sokos.utleggstrekk.domene.ske.Trekkstatus.AKTIV

class BehandleTrekkServiceTest :
    BehaviorSpec(
        {

            fun setUpBehandleTrekkService(
                alleTrekkSomIkkeErSendt: List<UtleggstrekkTable>,
                allePerioderForTrekkVersjon: List<TrekkPeriodeTable>,
                allePerioderForTrekkId: List<TrekkPeriodeTable>,
            ): BehandleTrekkService {
                val databaseServiceMock2 =
                    mockk<DatabaseService> {
                        coEvery { hentAlleTrekkSomIkkeErSendt() } returns alleTrekkSomIkkeErSendt
                        coEvery { hentAllePerioderForTrekkVersjon(any<UtleggstrekkTable>()) } returns allePerioderForTrekkVersjon
                        coEvery { hentAllePerioderForTrekkId(any<UtleggstrekkTable>()) } returns allePerioderForTrekkId
                        coEvery { lagreGenerertePerioder(any<List<TrekkPeriodeTable>>()) } just Runs
                    }

                return BehandleTrekkService(databaseServiceMock2)
            }
            Given("Trekkdokument dannes") {
                val trekkVersjon = 1
                val alleTrekkSomIkkeErSendt = listOf(trekkTable1(trekkVersjon))
                val allePerioderForTrekkVersjon = periodetableLOPM(trekkVersjon)
                val allePerioderForTrekkId = allePerioderForTrekkVersjon

                val result: Map<UtleggstrekkTable, List<TrekkTilOppdrag>> =
                    setUpBehandleTrekkService(
                        alleTrekkSomIkkeErSendt,
                        allePerioderForTrekkVersjon,
                        allePerioderForTrekkId,
                    ).lagTrekkSomSkalSendes()

                val trekkTilOppdrag =
                    result.values
                        .first()
                        .first()
                        .dokument.innrapporteringTrekk
                Then("Skal datoer formatteres på yyyy-mm-dd format") {
                    trekkTilOppdrag.prioritetFomDato shouldBe "2024-06-16"
                }

                And("Skal kodeTrekkType være TRK1") {
                    trekkTilOppdrag.kodeTrekktype shouldBe "TRK1" // TODO: Lagre som global konstant
                }

                And("Skal kilde være SOKOSUTLEGG") {
                    trekkTilOppdrag.kilde shouldBe "SOKOSUTLEGG" // TODO: Lagre som global konstant
                }
            }

            Given("Et mottatt trekk har én periode") {
                And("Perioden har kun 1 trekkalternativ") {
                    When("Trekket har trekkversjon 1") {
                        val trekkVersjon = 1

                        val alleTrekkSomIkkeErSendt = listOf(trekkTable1(trekkVersjon))

                        val allePerioderForTrekkVersjon = periodetableLOPM(trekkVersjon)
                        val allePerioderForTrekkId = allePerioderForTrekkVersjon

                        val result: Map<UtleggstrekkTable, List<TrekkTilOppdrag>> =
                            setUpBehandleTrekkService(
                                alleTrekkSomIkkeErSendt,
                                allePerioderForTrekkVersjon,
                                allePerioderForTrekkId,
                            ).lagTrekkSomSkalSendes()
                        Then("Skal trekket bli til 1 NYTT trekk med 3 perioder") {
                            result.size shouldBe 1

                            val utleggsTrekkIDatabase = result.keys
                            val alleTrekkTilOppdrag = result.values

                            utleggsTrekkIDatabase.size shouldBe 1
                            alleTrekkTilOppdrag.size shouldBe 1

                            alleTrekkSomIkkeErSendt.size shouldBe 1
                            utleggsTrekkIDatabase.first() shouldBe alleTrekkSomIkkeErSendt.first()

                            val trekkTilOppdrag =
                                alleTrekkTilOppdrag
                                    .first()
                                    .first()
                                    .dokument.innrapporteringTrekk
                            trekkTilOppdrag.perioder.periode.size shouldBe 3
                            trekkTilOppdrag.aksjonskode shouldBe Aksjonskode.NY
                        }
                    }
                    When("Trekket har trekkversjon 2") {
                        val trekkVersjon = 2
                        val alleTrekkSomIkkeErSendt = listOf(trekkTable1(trekkVersjon))
                        val allePerioderForTrekkVersjon = periodetableLOPM(trekkVersjon)

                        And("Vi har fått trekkversjon 1") {
                            val eksisterendeTrekk = periodetableLOPM(1)
                            val allePerioderForTrekkId = allePerioderForTrekkVersjon + eksisterendeTrekk

                            val result: Map<UtleggstrekkTable, List<TrekkTilOppdrag>> =
                                setUpBehandleTrekkService(
                                    alleTrekkSomIkkeErSendt,
                                    allePerioderForTrekkVersjon,
                                    allePerioderForTrekkId,
                                ).lagTrekkSomSkalSendes()
                            Then("Skal trekket bli til 1 ENDRET trekk med 3 perioder") {
                                result.size shouldBe 1
                                result.keys.first() shouldBe alleTrekkSomIkkeErSendt.first()
                                result.values.size shouldBe 1
                                val trekkTilOppdrag =
                                    result.values
                                        .first()
                                        .first()
                                        .dokument.innrapporteringTrekk
                                trekkTilOppdrag.perioder.periode.size shouldBe 3
                                trekkTilOppdrag.aksjonskode shouldBe Aksjonskode.ENDR
                            }
                        }

                        And("Vi ikke har fått trekkversjon 1") {
                            val allePerioderForTrekkId = allePerioderForTrekkVersjon
                            val result: Map<UtleggstrekkTable, List<TrekkTilOppdrag>> =
                                setUpBehandleTrekkService(
                                    alleTrekkSomIkkeErSendt,
                                    allePerioderForTrekkVersjon,
                                    allePerioderForTrekkId,
                                ).lagTrekkSomSkalSendes()

                            Then("Skal trekket bli til 1 NYTT trekk med 3 perioder") {
                                result.size shouldBe 1
                                result.keys.first() shouldBe alleTrekkSomIkkeErSendt.first()
                                result.values.size shouldBe 1
                                val trekkTilOppdrag =
                                    result.values
                                        .first()
                                        .first()
                                        .dokument.innrapporteringTrekk
                                trekkTilOppdrag.perioder.periode.size shouldBe 3
                                trekkTilOppdrag.aksjonskode shouldBe Aksjonskode.NY
                            }
                        }
                    }
                }
            }

            Given("Et mottatt trekk har 3 perioder") {
                And("Trekket har trekkversjon 1") {
                    val trekkVersjon = 1
                    val alleTrekkSomIkkeErSendt = listOf(trekkTable1(trekkVersjon))

                    And("Periodene i trekket har aksjonskodene LOPM og LOPP") {
                        val allePerioderForTrekkVersjon = periodetableLOPM(trekkVersjon) + periodetableLOPP(trekkVersjon)
                        val allePerioderForTrekkId = allePerioderForTrekkVersjon

                        val result: Map<UtleggstrekkTable, List<TrekkTilOppdrag>> =
                            setUpBehandleTrekkService(
                                alleTrekkSomIkkeErSendt,
                                allePerioderForTrekkVersjon,
                                allePerioderForTrekkId,
                            ).lagTrekkSomSkalSendes()

                        Then("Skal trekket bli til to NYE trekk") {
                            result.values.first().size shouldBe 2
                            val trekkTilOppdragVersjon1 =
                                result.values
                                    .first()
                                    .first()
                                    .dokument.innrapporteringTrekk

                            trekkTilOppdragVersjon1.perioder.periode.size shouldBe 6
                            trekkTilOppdragVersjon1.aksjonskode shouldBe Aksjonskode.NY

                            val trekkTilOppdragVersjon2 =
                                result.values
                                    .first()
                                    .last()
                                    .dokument.innrapporteringTrekk

                            trekkTilOppdragVersjon2.perioder.periode.size shouldBe 6
                            trekkTilOppdragVersjon2.aksjonskode shouldBe Aksjonskode.NY
                        }
                    }
                }

                And("Trekket har trekkversjon 2") {
                    val trekkVersjon = 2

                    And("Versjon 2 har trekkstatus AKTIV") {
                        // TODO: Hva hvis de endrer perioden (hva hvis de sender ingen perioder?) og trekkstatus er aktiv?
                        val alleTrekkSomIkkeErSendt = listOf(trekkTable1(trekkVersjon, Trekkstatus.AKTIV))

                        And("Periodene i trekkversjon 1 har trekkalternativ LOPM") {
                            val eksisterendeTrekk = periodetableLOPM(1)

                            And("Periodene i versjon 2 har aksjonskodene LOPM og LOPP") {
                                val allePerioderForTrekkVersjon = periodetableLOPM(trekkVersjon) + periodetableLOPP(trekkVersjon)
                                val allePerioderForTrekkId = allePerioderForTrekkVersjon + eksisterendeTrekk
                                val result: Map<UtleggstrekkTable, List<TrekkTilOppdrag>> =
                                    setUpBehandleTrekkService(
                                        alleTrekkSomIkkeErSendt,
                                        allePerioderForTrekkVersjon,
                                        allePerioderForTrekkId,
                                    ).lagTrekkSomSkalSendes()

                                Then("Skal trekket bli til to trekk: et NYTT og et ENDRET") {
                                    result.values.first().size shouldBe 2
                                    val trekkTilOppdrag =
                                        result.values
                                            .first()
                                            .first()
                                            .dokument.innrapporteringTrekk
                                    trekkTilOppdrag.perioder.periode.size shouldBe 6
                                    trekkTilOppdrag.aksjonskode shouldBe Aksjonskode.NY

                                    val trekkTilOppdragVersjon2 =
                                        result.values
                                            .first()
                                            .last()
                                            .dokument.innrapporteringTrekk

                                    trekkTilOppdragVersjon2.perioder.periode.size shouldBe 6
                                    trekkTilOppdragVersjon2.aksjonskode shouldBe Aksjonskode.ENDR
                                }
                            }
                            And("Periodene i versjon 2 har kun aksjonskode LOPP") {
                                val allePerioderForTrekkVersjon = periodetableLOPP(trekkVersjon)
                                val allePerioderForTrekkId = allePerioderForTrekkVersjon + eksisterendeTrekk
                                val result: Map<UtleggstrekkTable, List<TrekkTilOppdrag>> =
                                    setUpBehandleTrekkService(
                                        alleTrekkSomIkkeErSendt,
                                        allePerioderForTrekkVersjon,
                                        allePerioderForTrekkId,
                                    ).lagTrekkSomSkalSendes()
                                Then("Skal trekket bli til to trekk: et NYTT og et ENDRET") {
                                    result.values.first().size shouldBe 2
                                    val trekkTilOppdrag =
                                        result.values
                                            .first()
                                            .first()
                                            .dokument.innrapporteringTrekk
                                    trekkTilOppdrag.perioder.periode.size shouldBe 3
                                    trekkTilOppdrag.aksjonskode shouldBe Aksjonskode.ENDR

                                    val trekkTilOppdragVersjon2 =
                                        result.values
                                            .first()
                                            .last()
                                            .dokument.innrapporteringTrekk

                                    trekkTilOppdragVersjon2.perioder.periode.size shouldBe 3
                                    trekkTilOppdragVersjon2.aksjonskode shouldBe Aksjonskode.NY
                                }
                            }
                        }
                    }

                    And("Versjon 2 har trekkstatus AVSLUTTET") {
                        val alleTrekkSomIkkeErSendt = listOf(trekkTable1(trekkVersjon, Trekkstatus.AVSLUTTET))

                        And("Versjon 2 har ingen perioder") {
                            val allePerioderForTrekkVersjon = emptyList<TrekkPeriodeTable>()
                            And("Versjon 1 har trekkalternativ LOPP") {
                                val allePerioderForTrekkId = periodetableLOPP(1) + allePerioderForTrekkVersjon

                                val result: Map<UtleggstrekkTable, List<TrekkTilOppdrag>> =
                                    setUpBehandleTrekkService(
                                        alleTrekkSomIkkeErSendt,
                                        allePerioderForTrekkVersjon,
                                        allePerioderForTrekkId,
                                    ).lagTrekkSomSkalSendes()

                                Then("Skal ett trekk ha aksjonskode OPPH") {
                                    result.values.size shouldBe 1
                                    result.values.first().size shouldBe 1
                                    val trekkTilOppdrag =
                                        result.values
                                            .first()
                                            .first()
                                            .dokument.innrapporteringTrekk
                                    trekkTilOppdrag.aksjonskode shouldBe Aksjonskode.OPPH
                                    trekkTilOppdrag.perioder.periode
                                        .shouldBeEmpty()
                                }
                            }
                            And("Versjon 1 har trekkalternativ LOPM og LOPP") {
                                val allePerioderForTrekkId = periodetableLOPM(1) + periodetableLOPP(1) + allePerioderForTrekkVersjon

                                val result: Map<UtleggstrekkTable, List<TrekkTilOppdrag>> =
                                    setUpBehandleTrekkService(
                                        alleTrekkSomIkkeErSendt,
                                        allePerioderForTrekkVersjon,
                                        allePerioderForTrekkId,
                                    ).lagTrekkSomSkalSendes()
                                Then("Skal to trekk ha aksjonskode OPPH") {
                                    result.size shouldBe 1
                                    result.values.first().size shouldBe 2
                                    val trekkTilOppdrag =
                                        result.values
                                            .first()
                                            .first()
                                            .dokument.innrapporteringTrekk
                                    trekkTilOppdrag.aksjonskode shouldBe Aksjonskode.OPPH
                                    result.values
                                        .first()
                                        .last()
                                        .dokument.innrapporteringTrekk.aksjonskode shouldBe Aksjonskode.OPPH
                                }
                                And("Det er ikke perioder i avsluttet trekk") {
                                    val trekkTilOppdrag =
                                        result.values
                                            .first()
                                            .first()
                                            .dokument.innrapporteringTrekk
                                    trekkTilOppdrag.perioder.periode
                                        .shouldBeEmpty()
                                }
                            }
                        }
                        And("Versjon 2 har perioder") {
                            val allePerioderForTrekkVersjon = periodetableLOPP(2)
                            And("Trekkversjon 1 har trekkalternativ LOPP") {
                                val allePerioderForTrekkId = periodetableLOPP(1)

                                val result: Map<UtleggstrekkTable, List<TrekkTilOppdrag>> =
                                    setUpBehandleTrekkService(
                                        alleTrekkSomIkkeErSendt,
                                        allePerioderForTrekkVersjon,
                                        allePerioderForTrekkId,
                                    ).lagTrekkSomSkalSendes()

                                Then("Skal ett trekk ha aksjonskode OPPH") {
                                    result.values.size shouldBe 1
                                    result.values.first().size shouldBe 1
                                    val trekkTilOppdrag =
                                        result.values
                                            .first()
                                            .first()
                                            .dokument.innrapporteringTrekk
                                    trekkTilOppdrag.aksjonskode shouldBe Aksjonskode.OPPH
                                }
                                And("Det er perioder i avsluttet trekk") {
                                    val trekkTilOppdrag =
                                        result.values
                                            .first()
                                            .first()
                                            .dokument.innrapporteringTrekk
                                    trekkTilOppdrag.perioder.periode
                                        .shouldNotBeEmpty()
                                }
                            }
                        }
                    }
                }
            }
        },
    )

private fun trekkTable1(trekkVersjon: Int, trekkStatus: Trekkstatus = AKTIV) =
    UtleggstrekkTable(
        utleggstrekkTableId = 1,
        trekkidNavLOPP = null,
        trekkidNavLOPM = null,
        trekkidSke = "SKEID",
        trekkversjon = trekkVersjon,
        sekvensnummer = 1,
        saksnummer = "sak01",
        opprettetSke = Instant.parse("2024-06-16T13:33:05.672Z").toLocalDateTime(TimeZone.currentSystemDefault()),
        trekkpliktig = "987654321",
        skyldner = "12345678901",
        trekkstatus = trekkStatus,
        status = MOTTATT,
        kid = "12345654321",
        kontonummer = "12341212345",
        betalingsmottaker = "987654322",
        corrid = "dette_er_corrid_1",
        tidspunktSisteStatus = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
        tidspunktSendtOs = null,
        tidspunktOpprettet = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
    )

private fun periodetableLOPM(trekkVersjon: Int) =
    listOf(
        TrekkPeriodeTable(
            trekkPeriodeTableId = 1,
            sekvensnummer = 1,
            trekkidSke = "SKEID",
            trekkversjon = trekkVersjon,
            datoStart = "2025-01-01",
            datoSlutt = "2025-02-28",
            sats = 2000.00,
            trekkAlternativ = LOPM,
        ),
        TrekkPeriodeTable(
            trekkPeriodeTableId = 1,
            sekvensnummer = 1,
            trekkidSke = "SKEID",
            trekkversjon = trekkVersjon,
            datoStart = "2025-03-01",
            datoSlutt = "2025-04-30",
            trekkAlternativ = LOPM,
            sats = 1000.00,
        ),
        TrekkPeriodeTable(
            trekkPeriodeTableId = 1,
            sekvensnummer = 1,
            trekkidSke = "SKEID",
            trekkversjon = trekkVersjon,
            datoStart = "2025-05-01",
            datoSlutt = "2025-05-31",
            sats = 2500.00,
            trekkAlternativ = LOPM,
        ),
    )

private fun periodetableLOPP(trekkVersjon: Int) =
    listOf(
        TrekkPeriodeTable(
            trekkPeriodeTableId = 1,
            sekvensnummer = 1,
            trekkidSke = "SKEID",
            trekkversjon = trekkVersjon,
            datoStart = "2025-02-01",
            datoSlutt = "2025-02-28",
            sats = 20.00,
            trekkAlternativ = LOPP,
        ),
        TrekkPeriodeTable(
            trekkPeriodeTableId = 1,
            sekvensnummer = 1,
            trekkidSke = "SKEID",
            trekkversjon = trekkVersjon,
            datoStart = "2025-04-01",
            datoSlutt = "2025-04-30",
            trekkAlternativ = LOPP,
            sats = 10.00,
        ),
        TrekkPeriodeTable(
            trekkPeriodeTableId = 1,
            sekvensnummer = 1,
            trekkidSke = "SKEID",
            trekkversjon = trekkVersjon,
            datoStart = "2025-06-01",
            datoSlutt = "2025-06-31",
            sats = 25.00,
            trekkAlternativ = LOPP,
        ),
    )