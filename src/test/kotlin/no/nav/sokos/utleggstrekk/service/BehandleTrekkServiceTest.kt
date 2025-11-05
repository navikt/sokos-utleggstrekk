package no.nav.sokos.utleggstrekk.service

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldBeEmpty
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk

import no.nav.sokos.utleggstrekk.database.RepositoryNy
import no.nav.sokos.utleggstrekk.database.model.BetalingsinformasjonFraSkatt
import no.nav.sokos.utleggstrekk.database.model.PeriodeFraSkatt
import no.nav.sokos.utleggstrekk.database.model.PeriodeTilOS
import no.nav.sokos.utleggstrekk.database.model.TrekkFraSkatt
import no.nav.sokos.utleggstrekk.domene.nav.Aksjonskode
import no.nav.sokos.utleggstrekk.domene.nav.TrekkAlternativ
import no.nav.sokos.utleggstrekk.domene.ske.Trekkstatus

class BehandleTrekkServiceTest :
    BehaviorSpec({

        afterTest {
            clearAllMocks()
        }

        val gyldigTomDatoAvslutt = LocalDate.now().minusDays(1).toString()

        fun lagTestTrekkFraSkatt(trekkVersjon: Int = 1): TrekkFraSkatt {
            val tabellEntryId = 1L
            val trekkVersjon = trekkVersjon
            val sekvensnummer = 1
            val trekkId = "2a"
            val opprettet = "2024-06-16T13:33:05.672Z"
            val saksnummer = "Test_Beløp1"
            val trekkpliktig = "12345678901"
            val skyldner = "10987654321"
            val trekkstatus = Trekkstatus.AKTIV.name

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

        fun lagPeriodeForTrekkFraSkatt(
            trekkFraSkatt: TrekkFraSkatt,
            trekkAlternativ: TrekkAlternativ,
            startDato: String = "2025-01-01",
            sluttDato: String = "2025-03-31",
            sats: Double,
        ): PeriodeFraSkatt {
            val tabellEntryId = 1L
            val fraSkattId = trekkFraSkatt.id
            val trekkIdSke = trekkFraSkatt.trekkid
            val startDato = startDato
            val sluttDato = sluttDato
            val trekkBelop = if (trekkAlternativ == TrekkAlternativ.LOPM) sats else null
            val trekkProsent = if (trekkAlternativ == TrekkAlternativ.LOPP) sats else null
            return PeriodeFraSkatt(tabellEntryId, fraSkattId, trekkIdSke, startDato, sluttDato, trekkBelop, trekkProsent)
        }

        fun lagPerioderForSkattLOPP(trekkFraSkatt: TrekkFraSkatt): List<PeriodeFraSkatt> {
            val perioderEn = lagPeriodeForTrekkFraSkatt(trekkFraSkatt, TrekkAlternativ.LOPP, "2025-01-01", "2025-03-31", 20.0)
            val perioderTo = lagPeriodeForTrekkFraSkatt(trekkFraSkatt, TrekkAlternativ.LOPP, "2025-04-01", "2025-05-31", 15.0)
            val perioderTre = lagPeriodeForTrekkFraSkatt(trekkFraSkatt, TrekkAlternativ.LOPP, "2025-06-01", "2025-07-31", 10.0)

            return listOf(perioderEn, perioderTo, perioderTre)
        }

        fun lagPerioderForSkattLOPM(trekkFraSkatt: TrekkFraSkatt): List<PeriodeFraSkatt> {
            val perioderEn = lagPeriodeForTrekkFraSkatt(trekkFraSkatt, TrekkAlternativ.LOPM, "2025-01-01", "2025-03-31", 3000.0)
            val perioderTo = lagPeriodeForTrekkFraSkatt(trekkFraSkatt, TrekkAlternativ.LOPM, "2025-04-01", "2025-05-31", 2000.0)
            val perioderTre = lagPeriodeForTrekkFraSkatt(trekkFraSkatt, TrekkAlternativ.LOPM, "2025-06-01", "2025-07-31", 1000.0)

            return listOf(perioderEn, perioderTo, perioderTre)
        }

        fun List<PeriodeFraSkatt>.toKnownPeriods() =
            map {
                PeriodeTilOS(
                    sats = it.trekkprosent ?: it.trekkbeloep ?: 0.0,
                    periodeFomDato = it.startdato,
                    periodeTomDato = it.sluttdato,
                )
            }

        fun lagBetalingsinformasjonForTrekkFraSkatt(trekkFraSkatt: TrekkFraSkatt): BetalingsinformasjonFraSkatt {
            val betalingsmottaker = "971648198"
            val kidnummer = "17654202404"
            val kontonummer = "76940512057"
            return BetalingsinformasjonFraSkatt(1L, trekkFraSkatt.id, betalingsmottaker, kidnummer, kontonummer)
        }

        fun setUpBehandleTrekkServiceNy(
            alleTrekkSomIkkeErSendt: List<TrekkFraSkatt>,
            perioderForTrekkFraSkatt: List<PeriodeFraSkatt>,
            kjenteLOPPPerioder: List<PeriodeTilOS> = emptyList(),
            kjenteLOPMPerioder: List<PeriodeTilOS> = emptyList(),
        ): BehandleTrekkServiceNy {
            val betalingsinformasjonForTrekkFraSkatt: BetalingsinformasjonFraSkatt = lagBetalingsinformasjonForTrekkFraSkatt(alleTrekkSomIkkeErSendt.first())
            val repositoryNy = mockk<RepositoryNy>()
            val behandleTrekkServiceNy = BehandleTrekkServiceNy(repositoryNy)
            val trekkAlternativIOS =
                buildSet {
                    if (kjenteLOPMPerioder.isNotEmpty()) add(TrekkAlternativ.LOPM)
                    if (kjenteLOPPPerioder.isNotEmpty()) add(TrekkAlternativ.LOPP)
                }

            every { repositoryNy.getOsAlternativForTrekk(any()) } returns trekkAlternativIOS
            every { repositoryNy.getPerioderTilOs(any(), TrekkAlternativ.LOPM) } returns kjenteLOPMPerioder
            every { repositoryNy.getPerioderTilOs(any(), TrekkAlternativ.LOPP) } returns kjenteLOPPPerioder
            every { repositoryNy.getTrekkSomIkkeErBehandlet() } returns alleTrekkSomIkkeErSendt
            every { repositoryNy.getPerioderForTrekk(any()) } returns perioderForTrekkFraSkatt
            every { repositoryNy.getBetalingsinformasjonForTrekk(any()) } returns betalingsinformasjonForTrekkFraSkatt

            return behandleTrekkServiceNy
        }
        Given("Trekkdokument dannes") {

            val trekkVersjon = 1
            val trekkFraSkatt = lagTestTrekkFraSkatt(trekkVersjon)
            val alleTrekkSomIkkeErSendt = listOf(trekkFraSkatt)
            val betalingsinformasjonForTrekkFraSkatt: BetalingsinformasjonFraSkatt = lagBetalingsinformasjonForTrekkFraSkatt(trekkFraSkatt)

            val perioderForTrekkVersjon = listOf(lagPeriodeForTrekkFraSkatt(trekkFraSkatt, TrekkAlternativ.LOPM, sats = 5000.0))
            val periode = perioderForTrekkVersjon.first()
            val behandleTrekkServiceNy = setUpBehandleTrekkServiceNy(alleTrekkSomIkkeErSendt, perioderForTrekkVersjon, emptyList())

            When("Trekkdokument dannes for trekk fra skatt") {
                val trekkDokumenter = behandleTrekkServiceNy.lagTrekkDokument(trekkFraSkatt)
                trekkDokumenter.size shouldBe 1

                val trekkDokument = trekkDokumenter.first()

                val innrapporteringTrekk = trekkDokument.innrapporteringTrekk

                Then("Skal all informasjon være med") {
                    innrapporteringTrekk.aksjonskode shouldBe Aksjonskode.NY
                    innrapporteringTrekk.kreditorTrekkId shouldBe "${trekkFraSkatt.trekkid}${innrapporteringTrekk.kodeTrekkAlternativ.value}"
                    innrapporteringTrekk.kodeTrekkAlternativ shouldBe periode.trekkAlternativ()

                    innrapporteringTrekk.gyldigTomDato.shouldBeNull()
                    innrapporteringTrekk.perioder.periode.size shouldBe 1
                    innrapporteringTrekk.debitorId shouldBe trekkFraSkatt.skyldner
                    innrapporteringTrekk.kid shouldBe betalingsinformasjonForTrekkFraSkatt.kidnummer
                    innrapporteringTrekk.kreditorsRef shouldBe trekkFraSkatt.saksnummer
                    innrapporteringTrekk.saldo shouldBe 0.0
                    innrapporteringTrekk.navTrekkId.shouldBeEmpty()

                    innrapporteringTrekk.prioritetFomDato shouldBe
                        Instant
                            .parse(trekkFraSkatt.opprettet)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                            .toString()
                    innrapporteringTrekk.perioder.periode
                        .first()
                        .periodeFomDato shouldBe periode.startdato
                    innrapporteringTrekk.perioder.periode
                        .first()
                        .periodeTomDato shouldBe periode.sluttdato
                }

                Then("Skal datoer formatteres på yyyy-mm-dd format") {

                    val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                    innrapporteringTrekk.perioder.periode.forEach {

                        LocalDate.parse(it.periodeTomDato, dateFormatter).format(dateFormatter) shouldBe it.periodeTomDato
                        LocalDate.parse(it.periodeFomDato, dateFormatter).format(dateFormatter) shouldBe it.periodeFomDato
                    }
                }

                And("Skal kodeTrekkType være $KODE_TREKKTYPE") {
                    innrapporteringTrekk.kodeTrekktype shouldBe KODE_TREKKTYPE
                }

                And("Skal kilde være $KILDE") {
                    innrapporteringTrekk.kilde shouldBe KILDE
                }
            }
        }

        Given("Et mottatt trekk har én periode") {
            And("Perioden har kun trekkalternativ LOPP") {
                When("Trekket har trekkversjon 1") {
                    val trekkVersjon = 1
                    val trekkFraSkatt = lagTestTrekkFraSkatt(trekkVersjon)
                    val alleTrekkSomIkkeErSendt = listOf(trekkFraSkatt)

                    Then("Skal trekket bli til 1 NYTT trekk med 1 periode") {
                        val perioderForTrekkVersjon = lagPerioderForSkattLOPP(trekkFraSkatt).take(1)
                        val behandleTrekkServiceNy = setUpBehandleTrekkServiceNy(alleTrekkSomIkkeErSendt, perioderForTrekkVersjon, emptyList())

                        val trekkDokumenter = behandleTrekkServiceNy.lagTrekkDokument(alleTrekkSomIkkeErSendt.first())
                        trekkDokumenter shouldHaveSize 1
                        with(trekkDokumenter.first().innrapporteringTrekk) {
                            aksjonskode shouldBe Aksjonskode.NY
                            perioder.periode shouldHaveSize 1
                        }
                    }
                }

                When("Trekket har trekkversjon 2") {
                    val trekkVersjon = 2
                    val trekkFraSkatt = lagTestTrekkFraSkatt(trekkVersjon)
                    val alleTrekkSomIkkeErSendt = listOf(trekkFraSkatt)
                    val perioderForTrekkVersjon = lagPerioderForSkattLOPP(trekkFraSkatt).take(1)

                    And("Vi ikke har fått trekkversjon 1") {
                        val behandleTrekkServiceNy = setUpBehandleTrekkServiceNy(alleTrekkSomIkkeErSendt, perioderForTrekkVersjon, emptyList())
                        val trekkDokumenter = behandleTrekkServiceNy.lagTrekkDokument(alleTrekkSomIkkeErSendt.first())

                        Then("Skal trekket bli til 1 NYTT trekk med 1 perioder") {
                            trekkDokumenter shouldHaveSize 1

                            with(trekkDokumenter.first().innrapporteringTrekk) {
                                aksjonskode shouldBe Aksjonskode.NY
                                perioder.periode shouldHaveSize 1
                            }
                        }
                    }

                    And("Vi har fått trekkversjon 1") {
                        And("trekkalternativ i versjon 1 er LOPP") {
                            val behandleTrekkServiceNy =
                                setUpBehandleTrekkServiceNy(
                                    alleTrekkSomIkkeErSendt,
                                    perioderForTrekkVersjon,
                                    kjenteLOPPPerioder = perioderForTrekkVersjon.toKnownPeriods(),
                                )

                            Then("Skall trekket bli til 1 ENDRET trekk met 1 periode") {
                                val trekkDokumenter = behandleTrekkServiceNy.lagTrekkDokument(alleTrekkSomIkkeErSendt.first())
                                trekkDokumenter shouldHaveSize 1

                                with(trekkDokumenter.first().innrapporteringTrekk) {
                                    aksjonskode shouldBe Aksjonskode.ENDR
                                    perioder.periode shouldHaveSize 1
                                }
                            }
                        }

                        And("trekkalternativ i versjon 1 er LOPM") {
                            val kjenteLopmPerioder = lagPerioderForSkattLOPM(trekkFraSkatt).toKnownPeriods()
                            val behandleTrekkServiceNy =
                                setUpBehandleTrekkServiceNy(
                                    alleTrekkSomIkkeErSendt,
                                    perioderForTrekkVersjon,
                                    kjenteLOPMPerioder = kjenteLopmPerioder,
                                )

                            val trekkDokumenter = behandleTrekkServiceNy.lagTrekkDokument(alleTrekkSomIkkeErSendt.first())
                            trekkDokumenter shouldHaveSize 2

                            Then("Skall 1. trekk bli til 1 NYTT trekk med 1 periode og trekkalternativ LOPP") {
                                with(trekkDokumenter.first().innrapporteringTrekk) {
                                    aksjonskode shouldBe Aksjonskode.NY
                                    kodeTrekkAlternativ shouldBe TrekkAlternativ.LOPP
                                    perioder.periode shouldHaveSize 1
                                    perioder.periode.first().sats shouldNotBe 0.0
                                }
                            }

                            Then("Skall 2. trekk bli til 1 ENDRET trekk med 1 periode med sats 0.0 og trekkalternativ LOPM") {
                                with(trekkDokumenter.last().innrapporteringTrekk) {
                                    aksjonskode shouldBe Aksjonskode.ENDR
                                    kodeTrekkAlternativ shouldBe TrekkAlternativ.LOPM
                                    perioder.periode shouldHaveSize 1
                                    perioder.periode.first().sats shouldBe 0.0
                                }
                            }
                        }

                        And("trekkstatus er AVSLUTTET") {
                            val avsluttetTrekkFraSkatt = trekkFraSkatt.copy(trekkstatus = Trekkstatus.AVSLUTTET.name)
                            val alleAvsluttetTrekkSomIkkeErSendt = listOf(avsluttetTrekkFraSkatt)
                            And("Trekket er ingen periode") {
                                val behandleTrekkServiceNy =
                                    setUpBehandleTrekkServiceNy(
                                        alleTrekkSomIkkeErSendt = alleAvsluttetTrekkSomIkkeErSendt,
                                        perioderForTrekkFraSkatt = emptyList(),
                                        kjenteLOPPPerioder = perioderForTrekkVersjon.toKnownPeriods(),
                                    )
                                Then("Skal 1 trekk bli til 1 ENDRET trekk med ingen periode og gyldigTomDate dagens -1") {
                                    val trekkDokumenter = behandleTrekkServiceNy.lagTrekkDokument(alleAvsluttetTrekkSomIkkeErSendt.first())
                                    // TODO: should return a document when status is cancelled and there's no periods
//                                    trekkDokumenter shouldHaveSize 1
//
//                                    with(trekkDokumenter.first().innrapporteringTrekk) {
//                                        aksjonskode shouldBe Aksjonskode.ENDR
//                                        gyldigTomDato shouldBe gyldigTomDatoAvslutt
//                                        perioder.periode shouldHaveSize 0
//                                    }
                                }
                            }

                            And("Trekket er 1 periode") {
                                val behandleTrekkServiceNy =
                                    setUpBehandleTrekkServiceNy(
                                        alleAvsluttetTrekkSomIkkeErSendt,
                                        perioderForTrekkVersjon,
                                        kjenteLOPPPerioder = perioderForTrekkVersjon.toKnownPeriods(),
                                    )
                                Then("Skal 1 trekk bli til 1 ENDRET trekk med 1 periode og gyldigTomDato dagens -1") {
                                    val trekkDokumenter = behandleTrekkServiceNy.lagTrekkDokument(alleAvsluttetTrekkSomIkkeErSendt.first())
                                    trekkDokumenter shouldHaveSize 1

                                    with(trekkDokumenter.first().innrapporteringTrekk) {
                                        aksjonskode shouldBe Aksjonskode.ENDR
                                        gyldigTomDato shouldBe gyldigTomDatoAvslutt
                                        perioder.periode shouldHaveSize 1
                                        perioder.periode.first().sats shouldNotBe 0.0
                                    }
                                }
                            }
                        }
                    }
                }
            }

            And("Perioden har kun trekkalternativ LOPM") {
                When("Trekket har trekkversjon 1") {
                    val trekkVersjon = 1
                    val trekkFraSkatt = lagTestTrekkFraSkatt(trekkVersjon)
                    val alleTrekkSomIkkeErSendt = listOf(trekkFraSkatt)

                    Then("Skal trekket bli til 1 NYTT trekk med 1 periode") {
                        val perioderForTrekkVersjon = lagPerioderForSkattLOPM(trekkFraSkatt).take(1)
                        val behandleTrekkServiceNy = setUpBehandleTrekkServiceNy(alleTrekkSomIkkeErSendt, perioderForTrekkVersjon, emptyList())

                        val trekkDokumenter = behandleTrekkServiceNy.lagTrekkDokument(alleTrekkSomIkkeErSendt.first())
                        trekkDokumenter shouldHaveSize 1

                        with(trekkDokumenter.first().innrapporteringTrekk) {
                            aksjonskode shouldBe Aksjonskode.NY
                            perioder.periode shouldHaveSize 1
                        }
                    }
                }

                When("Trekket har trekkversjon 2") {
                    val trekkVersjon = 2
                    val trekkFraSkatt = lagTestTrekkFraSkatt(trekkVersjon)
                    val alleTrekkSomIkkeErSendt = listOf(trekkFraSkatt)
                    val perioderForTrekkVersjon = lagPerioderForSkattLOPM(trekkFraSkatt).take(1)

                    And("Vi ikke har fått trekkversjon 1") {
                        val behandleTrekkServiceNy = setUpBehandleTrekkServiceNy(alleTrekkSomIkkeErSendt, perioderForTrekkVersjon, emptyList())
                        val trekkDokumenter = behandleTrekkServiceNy.lagTrekkDokument(alleTrekkSomIkkeErSendt.first())

                        Then("Skal trekket bli til 1 NYTT trekk med 1 perioder") {
                            trekkDokumenter shouldHaveSize 1
                            with(trekkDokumenter.first().innrapporteringTrekk) {
                                aksjonskode shouldBe Aksjonskode.NY
                                perioder.periode shouldHaveSize 1
                            }
                        }
                    }

                    And("Vi har fått trekkversjon 1") {
                        And("trekkalternativ i versjon 1 er LOPP") {
                            val kjenteLoppPerioder = lagPerioderForSkattLOPP(trekkFraSkatt).toKnownPeriods()
                            val behandleTrekkServiceNy =
                                setUpBehandleTrekkServiceNy(
                                    alleTrekkSomIkkeErSendt,
                                    perioderForTrekkVersjon,
                                    kjenteLOPPPerioder = kjenteLoppPerioder,
                                )

                            val trekkDokumenter = behandleTrekkServiceNy.lagTrekkDokument(alleTrekkSomIkkeErSendt.first())
                            trekkDokumenter shouldHaveSize 2
                            Then("Skall 1. trekk bli til 1 NYTT trekk med 1 periode og trekkalternativ LOPM") {
                                with(trekkDokumenter.first().innrapporteringTrekk) {
                                    aksjonskode shouldBe Aksjonskode.NY
                                    kodeTrekkAlternativ shouldBe TrekkAlternativ.LOPM
                                    perioder.periode shouldHaveSize 1
                                    perioder.periode.first().sats shouldNotBe 0.0
                                }
                            }

                            Then("Skall 1. trekk bli til 1 ENDRET trekk med 1 periode med sats 0.0 og trekkalternativ LOPP") {
                                with(trekkDokumenter.last().innrapporteringTrekk) {
                                    aksjonskode shouldBe Aksjonskode.ENDR
                                    kodeTrekkAlternativ shouldBe TrekkAlternativ.LOPP
                                    perioder.periode shouldHaveSize 1
                                    perioder.periode.first().sats shouldBe 0.0
                                }
                            }
                        }

                        And("trekkalternativ i versjon 1 er LOPM") {
                            val behandleTrekkServiceNy =
                                setUpBehandleTrekkServiceNy(
                                    alleTrekkSomIkkeErSendt,
                                    perioderForTrekkVersjon,
                                    kjenteLOPMPerioder = perioderForTrekkVersjon.toKnownPeriods(),
                                )

                            Then("Skall trekket bli til 1 ENDRET trekk met 1 periode") {
                                val trekkDokumenter = behandleTrekkServiceNy.lagTrekkDokument(alleTrekkSomIkkeErSendt.first())
                                trekkDokumenter shouldHaveSize 1
                                with(trekkDokumenter.first().innrapporteringTrekk) {
                                    aksjonskode shouldBe Aksjonskode.ENDR
                                    perioder.periode shouldHaveSize 1
                                    perioder.periode.first().sats shouldNotBe 0.0
                                }
                            }
                        }

                        And("trekkstatus er AVSLUTTET") {
                            val avsluttetTrekkFraSkatt = trekkFraSkatt.copy(trekkstatus = Trekkstatus.AVSLUTTET.name)
                            val alleAvsluttetTrekkSomIkkeErSendt = listOf(avsluttetTrekkFraSkatt)
                            And("Trekket er ingen periode") {
                                val behandleTrekkServiceNy =
                                    setUpBehandleTrekkServiceNy(
                                        alleTrekkSomIkkeErSendt = alleAvsluttetTrekkSomIkkeErSendt,
                                        perioderForTrekkFraSkatt = emptyList(),
                                        kjenteLOPMPerioder = perioderForTrekkVersjon.toKnownPeriods(),
                                    )
                                Then("Skal 1 trekk bli til 1 ENDRET trekk med ingen periode og gyldigTomDate dagens -1") {
                                    val trekkDokumenter = behandleTrekkServiceNy.lagTrekkDokument(alleAvsluttetTrekkSomIkkeErSendt.first())
                                    // TODO: should return a document when status is cancelled and there's no periods
//                                    trekkDokumenter shouldHaveSize 1
//
//                                    with(trekkDokumenter.first().innrapporteringTrekk) {
//                                        aksjonskode shouldBe Aksjonskode.ENDR
//                                        gyldigTomDato shouldBe gyldigTomDatoAvslutt
//                                        perioder.periode shouldHaveSize 0
//                                    }
                                }
                            }

                            And("Trekket er 1 periode") {
                                val behandleTrekkServiceNy =
                                    setUpBehandleTrekkServiceNy(
                                        alleAvsluttetTrekkSomIkkeErSendt,
                                        perioderForTrekkVersjon,
                                        kjenteLOPMPerioder = perioderForTrekkVersjon.toKnownPeriods(),
                                    )
                                Then("Skal 1 trekk bli til 1 ENDRET trekk med 1 periode og gyldigTomDato dagens -1") {
                                    val trekkDokumenter = behandleTrekkServiceNy.lagTrekkDokument(alleAvsluttetTrekkSomIkkeErSendt.first())
                                    trekkDokumenter shouldHaveSize 1

                                    with(trekkDokumenter.first().innrapporteringTrekk) {
                                        aksjonskode shouldBe Aksjonskode.ENDR
                                        gyldigTomDato shouldBe gyldigTomDatoAvslutt
                                        perioder.periode shouldHaveSize 1
                                        perioder.periode.first().sats shouldNotBe 0.0
                                    }
                                }
                            }
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