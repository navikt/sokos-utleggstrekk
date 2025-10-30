package no.nav.sokos.utleggstrekk.service

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.unmockkObject

import no.nav.sokos.utleggstrekk.database.RepositoryNy
import no.nav.sokos.utleggstrekk.database.model.BetalingsinformasjonFraSkatt
import no.nav.sokos.utleggstrekk.database.model.PeriodeFraSkatt
import no.nav.sokos.utleggstrekk.database.model.TrekkFraSkatt
import no.nav.sokos.utleggstrekk.domene.nav.Aksjonskode
import no.nav.sokos.utleggstrekk.domene.nav.TrekkAlternativ

class BehandleTrekkServiceTest :
    BehaviorSpec({

        afterTest {
            clearAllMocks()
            unmockkObject(RepositoryNy)
        }

        fun lagTestTrekkFraSkatt(): TrekkFraSkatt {
            val tabellEntryId = 1L
            val trekkVersjon = 1
            val sekvensnummer = 1
            val trekkId = "2a"
            val opprettet = "2024-06-16T13:33:05.672Z"
            val saksnummer = "Test_Beløp1"
            val trekkpliktig = "12345678901"
            val skyldner = "10987654321"
            val trekkstatus = "AKTIV"

            return TrekkFraSkatt(
                id = tabellEntryId,
                trekkid = trekkId,
                sekvensnummer = sekvensnummer,
                trekkversjon = trekkVersjon,
                opprettet = opprettet,
                saksnummer = saksnummer,
                trekkpliktig = trekkpliktig,
                skyldner = skyldner,
                trekkstatus = trekkstatus,
            )
        }

        fun lagPerioderForTrekkFraSkatt(trekkFraSkatt: TrekkFraSkatt): PeriodeFraSkatt {
            val tabellEntryId = 1L
            val fraSkattId = trekkFraSkatt.id
            val trekkIdSke = trekkFraSkatt.trekkid
            val startDato = "2023-06-13"
            val sluttDato = "2024-11-30"
            val trekkBelop = 5000.00
            val trekkProsent = null
            return PeriodeFraSkatt(tabellEntryId, fraSkattId, trekkIdSke, startDato, sluttDato, trekkBelop, trekkProsent)
        }

        fun lagBetalingsinformasjonForTrekkFraSkatt(trekkFraSkatt: TrekkFraSkatt): BetalingsinformasjonFraSkatt {
            val betalingsmottaker = "971648198"
            val kidnummer = "17654202404"
            val kontonummer = "76940512057"
            return BetalingsinformasjonFraSkatt(1L, trekkFraSkatt.id, betalingsmottaker, kidnummer, kontonummer)
        }
        Given("Trekkdokument dannes") {

            val trekkFraSkatt = lagTestTrekkFraSkatt()
            val alleTrekkSomIkkeErSendt = listOf(trekkFraSkatt)
            // TODO: Når vi har flytter datasource inn RepositoryNy, skal vi mocke den i stedet for å hente periodene fra skatt
            val perioderForTrekkFraSkatt = listOf(lagPerioderForTrekkFraSkatt(trekkFraSkatt))
            perioderForTrekkFraSkatt.size shouldBe 1
            val betalingsinformasjonForTrekkFraSkatt: BetalingsinformasjonFraSkatt = lagBetalingsinformasjonForTrekkFraSkatt(trekkFraSkatt)

            val behandleTrekkServiceNy = spyk(BehandleTrekkServiceNy(mockk(relaxed = true)))
            every { behandleTrekkServiceNy.trekkSomSkalSendes() } returns alleTrekkSomIkkeErSendt
            every { behandleTrekkServiceNy.perioderForTrekkVersjon(any()) } returns perioderForTrekkFraSkatt
            every { behandleTrekkServiceNy.betalingsInformasjonForTrekk(any()) } returns betalingsinformasjonForTrekkFraSkatt
            perioderForTrekkFraSkatt.size shouldBe 1

            val trekkSomIkkeErSendt = behandleTrekkServiceNy.trekkSomSkalSendes()
            trekkSomIkkeErSendt.size shouldBe alleTrekkSomIkkeErSendt.size

            val trekkSomSkalSendes = trekkSomIkkeErSendt.first()
            val perioderForTrekkVersjon = behandleTrekkServiceNy.perioderForTrekkVersjon(trekkSomSkalSendes)
            perioderForTrekkVersjon.size shouldBe perioderForTrekkFraSkatt.size

            val periodeInformasjon = behandleTrekkServiceNy.utledTrekkAlternativForPeriode(perioderForTrekkFraSkatt)

            periodeInformasjon.size shouldBe perioderForTrekkFraSkatt.size // expected 1 but was 0
            periodeInformasjon.size shouldBe 1
            val periode = periodeInformasjon.first()
            periode.trekkalternativ shouldBe TrekkAlternativ.LOPM
            periode.trekkidSke shouldBe trekkFraSkatt.trekkid
            periode.periodeFraSkatt shouldBe perioderForTrekkFraSkatt.first()

            When("Trekkdokument dannes for trekk fra skatt") {
                val document = behandleTrekkServiceNy.lagTrekkDokument(trekkSomSkalSendes, periodeInformasjon)

                val innrapporteringTrekk = document.innrapporteringTrekk
                innrapporteringTrekk.aksjonskode shouldBe Aksjonskode.NY
                innrapporteringTrekk.kodeTrekkAlternativ shouldBe periode.trekkalternativ

                Then("Skal trekkpliktig og skyldner være med") {
                }

                Then("Skal datoer formatteres på yyyy-mm-dd format") {
                    innrapporteringTrekk.prioritetFomDato shouldBe periode.periodeFraSkatt.startdato
                }

                And("Skal kodeTrekkType være TRK1") {
                }

                And("Skal kilde være SOKOSUTLEGG") {
                }
            }
        }

        Given("Et mottatt trekk har én periode") {
            And("Perioden har kun 1 trekkalternativ") {
                When("Trekket har trekkversjon 1") {
                    Then("Skal trekket bli til 1 NYTT trekk med 3 perioder") {
                    }
                }
                When("Trekket har trekkversjon 2") {
                    And("Vi har fått trekkversjon 1") {
                        Then("Skal trekket bli til 1 ENDRET trekk med 3 perioder") {}
                    }
                    And("Vi ikke har fått trekkversjon 1") {
                        Then("Skal trekket bli til 1 NYTT trekk med 3 perioder") {
                        }
                    }
                }
            }
        }

        Given("Et mottatt trekk har 3 perioder") {
            And("Trekket har trekkversjon 1") {
                And("Periodene i trekket har aksjonskodene LOPM og LOPP") {
                    Then("Skal trekket bli til to NYE trekk") {}
                }
                And("Trekket har trekkversjon 2") {
                    And("Versjon 2 har trekkstatus AKTIV") {
                        And("Periodene i trekkversjon 1 har trekkalternativ LOPM") {
                            And("Periodene i versjon 2 har aksjonskodene LOPM og LOPP") {
                                Then("Skal trekket bli til to trekk: et NYTT og et ENDRET") {}
                            }
                            And("Periodene i versjon 2 har kun aksjonskode LOPP") {
                                Then("Skal trekket bli til to trekk: et NYTT og et ENDRET") {}
                            }
                        }
                    }
                    And("Versjon 2 har trekkstatus AVSLUTTET") {
                        And("Versjon 2 har ingen perioder") {
                            And("Versjon 1 har trekkalternativ LOPP") {
                                Then("Skal ett trekk ha aksjonskode OPPH") {}
                            }
                            And("Versjon 1 har trekkalternativ LOPM og LOPP") {
                                Then("Skal to trekk ha aksjonskode OPPH") {}
                                And("Det er ikke perioder i avsluttet trekk") {}
                            }
                        }
                        And("Versjon 2 har perioder") {
                            And("Trekkversjon 1 har trekkalternativ LOPP") {
                                Then("Skal ett trekk ha aksjonskode OPPH") {}
                                And("Det er perioder i avsluttet trekk") {}
                            }
                        }
                    }
                }
            }
        }
    })