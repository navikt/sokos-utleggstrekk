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
import no.nav.sokos.utleggstrekk.domene.ske.Trekkstatus
import no.nav.sokos.utleggstrekk.domene.ske.Trekkstatus.AKTIV

class BehandleTrekkServiceTest :
    BehaviorSpec(
        {
            val databaseServiceMock =
                mockk<DatabaseService> {
                    coEvery { lagreGenerertePerioder(any<List<TrekkPeriodeTable>>()) } just Runs
                }
            val behandleTrekkService = BehandleTrekkService(databaseServiceMock)

            Given("Trekkdokument dannes") {
                val trekkVersjon = 1 // TODO lag default verdi i function signatur
                val trekkITest = trekkTable1(trekkVersjon)
                val perioderiTest = periodetableLOPM(trekkVersjon)
                coEvery { databaseServiceMock.hentAlleTrekkSomIkkeErSendt() } returns listOf(trekkITest)
                coEvery { databaseServiceMock.hentAllePerioderForTrekkVersjon(any<UtleggstrekkTable>()) } returns perioderiTest
                coEvery { databaseServiceMock.hentAllePerioderForTrekkId(any<UtleggstrekkTable>()) } returns perioderiTest

                val result = behandleTrekkService.lagTrekkSomSkalSendes()
                Then("Skal datoer formatteres på yyyy-mm-dd format") {
                    result.values
                        .first()
                        .first()
                        .dokument.innrapporteringTrekk.prioritetFomDato shouldBe "2024-06-16"
                }

                Then("Skal kodeTrekkType være TRK1") {
                    result.values
                        .first()
                        .first()
                        .dokument.innrapporteringTrekk.kodeTrekktype shouldBe "TRK1" // TODO: Lagre som global konstant
                }

                Then("Skal kilde være SOKOSUTLEGG") {
                    result.values
                        .first()
                        .first()
                        .dokument.innrapporteringTrekk.kilde shouldBe "SOKOSUTLEGG" // TODO: Lagre som global konstant
                }
            }

            Given("Et mottatt trekk har én periode") {
                And("Perioden har kun 1 trekkalternativ") {
                    When("Trekket har trekkversjon 1") {
                        val trekkVersjon = 1
                        val trekkITest = trekkTable1(trekkVersjon)

                        val perioderiTest = periodetableLOPM(trekkVersjon)
                        coEvery { databaseServiceMock.hentAlleTrekkSomIkkeErSendt() } returns listOf(trekkITest)
                        coEvery { databaseServiceMock.hentAllePerioderForTrekkVersjon(any<UtleggstrekkTable>()) } returns perioderiTest
                        coEvery { databaseServiceMock.hentAllePerioderForTrekkId(any<UtleggstrekkTable>()) } returns perioderiTest

                        val result = behandleTrekkService.lagTrekkSomSkalSendes()
                        Then("Skal trekket bli til 1 NYTT trekk med 3 perioder") {
                            result.size shouldBe 1
                            result.keys.first() shouldBe trekkITest
                            result.values.size shouldBe 1

                            result.values
                                .first()
                                .first()
                                .dokument.innrapporteringTrekk.perioder.periode.size shouldBe 3
                            result.values
                                .first()
                                .first()
                                .dokument.innrapporteringTrekk.aksjonskode shouldBe Aksjonskode.NY
                        }
                    }
                    When("Trekket har trekkversjon 2") {
                        val trekkVersjon = 2
                        val trekkITest = trekkTable1(trekkVersjon)
                        val perioderiTest = periodetableLOPM(trekkVersjon)
                        And("Vi har fått trekkversjon 1") {
                            val eksisterendeTrekk = periodetableLOPM(1)

                            coEvery { databaseServiceMock.hentAlleTrekkSomIkkeErSendt() } returns listOf(trekkITest)
                            coEvery { databaseServiceMock.hentAllePerioderForTrekkVersjon(any<UtleggstrekkTable>()) } returns perioderiTest
                            coEvery { databaseServiceMock.hentAllePerioderForTrekkId(any<UtleggstrekkTable>()) } returns eksisterendeTrekk + perioderiTest

                            val result = behandleTrekkService.lagTrekkSomSkalSendes()
                            Then("Skal trekket bli til 1 ENDRET trekk med 3 perioder") {
                                result.size shouldBe 1
                                result.keys.first() shouldBe trekkITest
                                result.values.size shouldBe 1
                                result.values
                                    .first()
                                    .first()
                                    .dokument.innrapporteringTrekk.perioder.periode.size shouldBe 3
                                result.values
                                    .first()
                                    .first()
                                    .dokument.innrapporteringTrekk.aksjonskode shouldBe Aksjonskode.ENDR
                            }
                        }

                        And("Vi ikke har fått trekkversjon 1") {
                            val trekkITest = trekkTable1(trekkVersjon)
                            val perioderiTest = periodetableLOPM(trekkVersjon)
                            coEvery { databaseServiceMock.hentAlleTrekkSomIkkeErSendt() } returns listOf(trekkITest)
                            coEvery { databaseServiceMock.hentAllePerioderForTrekkVersjon(any<UtleggstrekkTable>()) } returns perioderiTest
                            coEvery { databaseServiceMock.hentAllePerioderForTrekkId(any<UtleggstrekkTable>()) } returns perioderiTest

                            val result = behandleTrekkService.lagTrekkSomSkalSendes()

                            Then("Skal trekket bli til 1 NYTT trekk med 3 perioder") {
                                result.size shouldBe 1
                                result.keys.first() shouldBe trekkITest
                                result.values.size shouldBe 1
                                result.values
                                    .first()
                                    .first()
                                    .dokument.innrapporteringTrekk.perioder.periode.size shouldBe 3
                                result.values
                                    .first()
                                    .first()
                                    .dokument.innrapporteringTrekk.aksjonskode shouldBe Aksjonskode.NY
                            }
                        }
                    }
                }
            }

            Given("Et mottatt trekk har 3 perioder") {
                And("Trekket har trekkversjon 1") {
                    val trekkVersjon = 1
                    val trekkITest = trekkTable1(trekkVersjon)
                    coEvery { databaseServiceMock.hentAlleTrekkSomIkkeErSendt() } returns listOf(trekkITest)
                    And("Periodene har aksjonskodene LOPM og LOPP") {
                        val perioderiTest = periodetableLOPM(trekkVersjon) + periodetableLOPP(trekkVersjon)

                        coEvery { databaseServiceMock.hentAllePerioderForTrekkVersjon(any<UtleggstrekkTable>()) } returns perioderiTest
                        coEvery { databaseServiceMock.hentAllePerioderForTrekkId(any<UtleggstrekkTable>()) } returns perioderiTest

                        val result = behandleTrekkService.lagTrekkSomSkalSendes()
                        Then("Skal trekket bli til to NYE trekk") {
                            result.values.first().size shouldBe 2
                            result.values
                                .first()
                                .first()
                                .dokument.innrapporteringTrekk.perioder.periode.size shouldBe 6
                            result.values
                                .first()
                                .first()
                                .dokument.innrapporteringTrekk.aksjonskode shouldBe Aksjonskode.NY
                            result.values
                                .first()
                                .last()
                                .dokument.innrapporteringTrekk.perioder.periode.size shouldBe 6
                            result.values
                                .first()
                                .last()
                                .dokument.innrapporteringTrekk.aksjonskode shouldBe Aksjonskode.NY
                        }
                    }
                }

                And("Trekket har trekkversjon 2") {
                    val trekkVersjon = 2

                    And("Versjon 2 har trekkstatus AKTIV") {
                        val mottattAktivtTrekk = trekkTable1(trekkVersjon, Trekkstatus.AKTIV)
                        And("Trekkversjon 1 har trekkalternativ LOPM") {
                            val eksisterendeTrekk = periodetableLOPM(1)
                            And("Periodene i versjon 2 har aksjonskodene LOPM og LOPP") {
                                val perioderiTest = periodetableLOPM(trekkVersjon) + periodetableLOPP(trekkVersjon)
                                coEvery { databaseServiceMock.hentAlleTrekkSomIkkeErSendt() } returns listOf(mottattAktivtTrekk)
                                coEvery { databaseServiceMock.hentAllePerioderForTrekkVersjon(any<UtleggstrekkTable>()) } returns perioderiTest
                                coEvery { databaseServiceMock.hentAllePerioderForTrekkId(any<UtleggstrekkTable>()) } returns eksisterendeTrekk + perioderiTest
                                val result = behandleTrekkService.lagTrekkSomSkalSendes()
                                Then("Skal trekket bli til to trekk: et NYTT og et ENDRET") {
                                    result.values.first().size shouldBe 2
                                    result.values
                                        .first()
                                        .first()
                                        .dokument.innrapporteringTrekk.perioder.periode.size shouldBe 6
                                    result.values
                                        .first()
                                        .first()
                                        .dokument.innrapporteringTrekk.aksjonskode shouldBe Aksjonskode.NY
                                    result.values
                                        .first()
                                        .last()
                                        .dokument.innrapporteringTrekk.perioder.periode.size shouldBe 6
                                    result.values
                                        .first()
                                        .last()
                                        .dokument.innrapporteringTrekk.aksjonskode shouldBe Aksjonskode.ENDR
                                }
                            }
                            And("Periodene i versjon 2 har kun aksjonskode LOPP") {
                                val perioderiTest = periodetableLOPP(trekkVersjon)
                                coEvery { databaseServiceMock.hentAlleTrekkSomIkkeErSendt() } returns listOf(mottattAktivtTrekk)
                                coEvery { databaseServiceMock.hentAllePerioderForTrekkVersjon(any<UtleggstrekkTable>()) } returns perioderiTest
                                coEvery { databaseServiceMock.hentAllePerioderForTrekkId(any<UtleggstrekkTable>()) } returns eksisterendeTrekk + perioderiTest
                                val result = behandleTrekkService.lagTrekkSomSkalSendes()
                                Then("Skal trekket bli til to trekk: et NYTT og et ENDRET") {
                                    result.values.first().size shouldBe 2
                                    result.values
                                        .first()
                                        .first()
                                        .dokument.innrapporteringTrekk.perioder.periode.size shouldBe 3
                                    result.values
                                        .first()
                                        .first()
                                        .dokument.innrapporteringTrekk.aksjonskode shouldBe Aksjonskode.ENDR
                                    result.values
                                        .first()
                                        .last()
                                        .dokument.innrapporteringTrekk.perioder.periode.size shouldBe 3
                                    result.values
                                        .first()
                                        .last()
                                        .dokument.innrapporteringTrekk.aksjonskode shouldBe Aksjonskode.NY
                                }
                            }
                        }
                    }

                    And("Versjon 2 har trekkstatus AVSLUTTET") {
                        val mottattAvsluttetTrekk = trekkTable1(trekkVersjon, Trekkstatus.AVSLUTTET)
                        And("Trekkversjon 1 har trekkalternativ LOPP") {
                            val perioderForEksisterendeTrekkSomSkalAvsluttes = periodetableLOPP(1)

                            And("Versjon 2 har ingen perioder") {
                                coEvery { databaseServiceMock.hentAlleTrekkSomIkkeErSendt() } returns listOf(mottattAvsluttetTrekk)
                                coEvery { databaseServiceMock.hentAllePerioderForTrekkVersjon(any<UtleggstrekkTable>()) } returns emptyList()
                                coEvery { databaseServiceMock.hentAllePerioderForTrekkId(any<UtleggstrekkTable>()) } returns perioderForEksisterendeTrekkSomSkalAvsluttes
                                val result = behandleTrekkService.lagTrekkSomSkalSendes()
                                Then("Skal ett trekk ha aksjonskode OPPH") {
                                    result.values.size shouldBe 1
                                    result.values.first().size shouldBe 1
                                    result.values
                                        .first()
                                        .first()
                                        .dokument.innrapporteringTrekk.aksjonskode shouldBe Aksjonskode.OPPH
                                    result.values
                                        .first()
                                        .first()
                                        .dokument.innrapporteringTrekk.perioder.periode
                                        .shouldBeEmpty()
                                }
                            }
                            And("Versjon 2 har perioder") {
                                val perioderForTrekkversjon = periodetableLOPP(1)

                                coEvery { databaseServiceMock.hentAlleTrekkSomIkkeErSendt() } returns listOf(mottattAvsluttetTrekk)
                                coEvery { databaseServiceMock.hentAllePerioderForTrekkVersjon(any<UtleggstrekkTable>()) } returns perioderForTrekkversjon
                                coEvery { databaseServiceMock.hentAllePerioderForTrekkId(any<UtleggstrekkTable>()) } returns perioderForEksisterendeTrekkSomSkalAvsluttes
                                val result = behandleTrekkService.lagTrekkSomSkalSendes()
                                Then("Skal ett trekk ha aksjonskode OPPH") {
                                    result.values.size shouldBe 1
                                    result.values.first().size shouldBe 1
                                    result.values
                                        .first()

                                    result.values
                                        .first()
                                        .first()
                                        .dokument.innrapporteringTrekk.perioder.periode
                                        .shouldNotBeEmpty()
                                }
                            }
                        }

                        And("Trekkversjon 1 har trekkalternativ LOPM og LOPP") {
                            val perioderForEksisterendeTrekkSomSkalAvsluttes = periodetableLOPM(1) + periodetableLOPP(1)

                            coEvery { databaseServiceMock.hentAlleTrekkSomIkkeErSendt() } returns listOf(mottattAvsluttetTrekk)
                            coEvery { databaseServiceMock.hentAllePerioderForTrekkVersjon(any<UtleggstrekkTable>()) } returns emptyList()
                            coEvery { databaseServiceMock.hentAllePerioderForTrekkId(any<UtleggstrekkTable>()) } returns perioderForEksisterendeTrekkSomSkalAvsluttes

                            val result = behandleTrekkService.lagTrekkSomSkalSendes()

                            Then("Skal to trekk ha aksjonskode OPPH") {
                                result.size shouldBe 1
                                result.values.first().size shouldBe 2
                                result.values
                                    .first()
                                    .first()
                                    .dokument.innrapporteringTrekk.aksjonskode shouldBe Aksjonskode.OPPH
                                result.values
                                    .first()
                                    .last()
                                    .dokument.innrapporteringTrekk.aksjonskode shouldBe Aksjonskode.OPPH
                            }
                        }
                    }
                }
            }

      /*


                test(
                    "Trekk med trekkversjon med to trekkalternativ skal avsluttes, BLir to avsluttet trekk forventer ikke perioder i avsluttet trekk",
                ) {
                    val  = 8
                    val trekkITest = trekkTable1().copy(trekkversjon = 2, trekkstatus = AVSLUTTET)
                    coEvery { databaseServiceMock.hentAlleTrekkSomIkkeErSendt() } returns listOf(trekkITest)
                    coEvery { databaseServiceMock.hentAllePerioderForTrekkVersjon(any<UtleggstrekkTable>()) } returns emptyList()
                    coEvery { databaseServiceMock.hentAllePerioderForTrekkId(any<UtleggstrekkTable>()) } returns
                        periodetable1() + periodetable2()
                    coEvery { databaseServiceMock.lagreGenerertePerioder(any<List<TrekkPeriodeTable>>()) } just Runs
                    val result = behandleTrekkService.lagTrekkSomSkalSendes()

                    result.size shouldBe 1
                    result.values.first().size shouldBe 2
                    result.values
                        .first()
                        .first()
                        .dokument.innrapporteringTrekk.aksjonskode.value shouldBe "OPPH"
                    result.values
                        .first()
                        .last()
                        .dokument.innrapporteringTrekk.aksjonskode.value shouldBe "OPPH"
                }*/
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
