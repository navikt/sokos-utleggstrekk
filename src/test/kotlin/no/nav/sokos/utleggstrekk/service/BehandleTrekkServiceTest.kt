package no.nav.sokos.utleggstrekk.service

import io.kotest.core.spec.style.BehaviorSpec

class BehandleTrekkServiceTest :
    BehaviorSpec({

        Given("Trekkdokument dannes") {
            Then("Skal datoer formatteres på yyyy-mm-dd format") {
            }

            And("Skal kodeTrekkType være TRK1") {
            }

            And("Skal kilde være SOKOSUTLEGG") {
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