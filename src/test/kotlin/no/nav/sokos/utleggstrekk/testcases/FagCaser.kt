package no.nav.sokos.utleggstrekk.testcases

import io.kotest.core.spec.style.BehaviorSpec

import no.nav.sokos.utleggstrekk.config.jsonConfig
import no.nav.sokos.utleggstrekk.domene.ske.Trekkpaalegg
import no.nav.sokos.utleggstrekk.listener.DBListener
import no.nav.sokos.utleggstrekk.listener.DBListener.RepositoryNy
import no.nav.sokos.utleggstrekk.service.BehandleTrekkServiceNy
import no.nav.sokos.utleggstrekk.util.resourceToString

/*
*  HVIS trekk har status avsluttet:
* Vi sender OPPH til OS
* OG vi skal sende  datogyldigtom = dagens dato minus 1
* */
class FagCaser :
    BehaviorSpec({

        extensions(DBListener)
        val behandleTrekkService = BehandleTrekkServiceNy(RepositoryNy)

        Given("Nytt trekk (som ikke endres)") {
            val skalSendes: Trekkpaalegg = jsonConfig.decodeFromString<List<Trekkpaalegg>>(resourceToString("InitTrekk/Fra_Skatt_Trekk1_versjon1_en_periode_belop.json")).first()
            val idForTrekk = RepositoryNy.saveTrekkpaalegg(skalSendes)

            When("Periode april 2026 - 24. juli 2026 - 25 prosent") {
                Then("TRK1 LOPP") {}
                And("Periode 1.april - 31. juli 2026 ") {}
                And("25 prosent") {}
                And(" Gyldig tom 24. juli 2026") {}
            }
        }

        Given("Trekkfri periode") {
            When("Nytt trekk fra januar 2026 på 10 %. Får endring med trekkfri periode mars 2026.") {
                Then("TRK1, LOPP. Periode januar og februar vises med sats 10, mars vises med sats 0 og april og fremover vises med sats 10.")
            }
        }

        Given("Skifte fra prosentsats til månedssats") {
            When("Nytt trekk fra februar 2026 på 20 %. Endres fra mars 2026 til månedssats på kr. 3.000") {
                Then("To trekk TRK1") {}
                And(" LOPP Periode januar og februar vises med sats 20 prosent ") {}
                And(" LOPM Periode mars og fremover vises med sats 3.000") {}
            }
        }

        Context("Nytt trekk") {
            When("Periode mai 2026 - 24. juli 2026: sats 2 000") {
                Then("TRK1 LOPM ") {}
                And("Periode 1.mai - 31. juli 2026") {}
                And("sats 2 000") {}
                And("Gyldig tom 24. juli 2026") {}
            }

            Given("Endring av trekk") {
                When("Periode juni -30. juni 2026 ") {
                    Then("sats 0 (trekkfri periode)") {}
                }
                When("1. august - 30 september") {
                    Then("sats 2 000 ") {}
                    And("tom dato endres til 30. september 2026") {}
                }
            }

            Given("Stopp av trekk") {
                When("Periode Skal stoppes fra og med 22. august 2026") {
                    Then("Periode 1.mai - 31. mai 2026: sats 2 000 ") {}
                    And("Periode 1. juni - 30. juni 2026: sats 0") {}
                    And("Periode 1.juli - 30. september 2026: sats 2 000") {}
                    And("Gyldig tom 22. august 2026") {}
                    And("TRK1 LOPM") {}
                }
            }
        }

        Context("Nytt trekk med betalingsplan") {
            Given("Tre perioder") {
                When("To satstyper") {
                    When("To perioder har månedssats") {
                        When("Periode februar - 30. oktober 2026: sats 5 000 kroner") {
                            When("Periode 1.november 2026 - 31. mai 2027: sats 0") {
                                Then("Skal det dannes ett trekk LOPM for disse to periodene") {}
                                And("Trekket skal ha to perioder ") {
                                    // 2.februar 2026 - 30. oktober 2026
                                    // 1.november 2026 - 31. mai 2027
                                }
                            }
                        }
                    }

                    When("En periode har prosent") {
                        When("Periode juni 2027 - 30.september 2027:  sats 25 prosent") {
                            Then("Skal det dannes ett trekk LOPP") {}
                            And("Trekket skal ha én periode") {}
                        }
                    }
                    Then("Skal det være totalt to trekk: ett LOPM og ett LOPP") {}
                }
            }

            Given("Endring av betalingsplan") {
                When("Periode 1. juni - 30. september 2027 får sats 2 500") {
                    Then("LOPP avsluttes/kanselleres") {}
                    And("LOPM endres: det legges til periode Periode 1. juni - 30. september 2027 - sats 2 500") {}
                }
            }

            Given("Stopp av trekk med betalingsplan") {
                When("Periode skal stoppes fra og med 25. april 2026") {
                    Then("LOPM settes gyldig tom 25. april 2026") {}
                }
            }
        }
    })