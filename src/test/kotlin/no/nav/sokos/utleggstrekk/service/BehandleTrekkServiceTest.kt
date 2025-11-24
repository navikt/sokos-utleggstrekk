package no.nav.sokos.utleggstrekk.service

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldBeEmpty
import io.kotest.matchers.string.shouldNotBeEmpty
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.invoke
import io.mockk.mockk
import kotliquery.TransactionalSession

import no.nav.sokos.utleggstrekk.database.RepositoryNy
import no.nav.sokos.utleggstrekk.database.model.BetalingsinformasjonFraSkatt
import no.nav.sokos.utleggstrekk.database.model.PeriodeFraSkatt
import no.nav.sokos.utleggstrekk.database.model.PeriodeTilOS
import no.nav.sokos.utleggstrekk.database.model.TrekkFraSkatt
import no.nav.sokos.utleggstrekk.domene.nav.Aksjonskode
import no.nav.sokos.utleggstrekk.domene.nav.OSDto
import no.nav.sokos.utleggstrekk.domene.nav.TrekkAlternativ
import no.nav.sokos.utleggstrekk.domene.ske.Trekkstatus

class BehandleTrekkServiceTest :
    BehaviorSpec({
        val capturedOSDtos = mutableListOf<OSDto>()

        afterContainer {
            capturedOSDtos.clear()
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
            alleTrekkSomIkkeErBehandlet: List<TrekkFraSkatt>,
            perioderForTrekkFraSkatt: List<PeriodeFraSkatt>,
            kjenteLOPPPerioder: List<PeriodeTilOS> = emptyList(),
            kjenteLOPMPerioder: List<PeriodeTilOS> = emptyList(),
        ): BehandleTrekkServiceNy {
            val betalingsinformasjonForTrekkFraSkatt: BetalingsinformasjonFraSkatt = lagBetalingsinformasjonForTrekkFraSkatt(alleTrekkSomIkkeErBehandlet.first())

            val repositoryNy = mockk<RepositoryNy>()
            val behandleTrekkServiceNy = BehandleTrekkServiceNy(repositoryNy)
            val trekkAlternativIOS =
                buildSet {
                    if (kjenteLOPMPerioder.isNotEmpty()) add(TrekkAlternativ.LOPM)
                    if (kjenteLOPPPerioder.isNotEmpty()) add(TrekkAlternativ.LOPP)
                }

            // Mock withTransaction
            every { repositoryNy.withTransaction<Any?>(captureLambda()) } answers {
                // Execute the captured lambda with a session mock
                val session = mockk<TransactionalSession>()
                lambda<(TransactionalSession) -> Any?>().invoke(session)
            }

            every { repositoryNy.getOsAlternativForTrekk(any()) } returns trekkAlternativIOS
            every { repositoryNy.getPerioderTilOs(any(), TrekkAlternativ.LOPM) } returns kjenteLOPMPerioder
            every { repositoryNy.getPerioderTilOs(any(), TrekkAlternativ.LOPP) } returns kjenteLOPPPerioder
            every { repositoryNy.getTrekkSomIkkeErBehandlet() } returns alleTrekkSomIkkeErBehandlet
            every { repositoryNy.getPerioderForTrekk(any()) } returns perioderForTrekkFraSkatt
            every { repositoryNy.getBetalingsinformasjonForTrekk(any()) } returns betalingsinformasjonForTrekkFraSkatt
            every { repositoryNy.insertTransaksjonTilOs(capture(capturedOSDtos), any()) } returns Unit
            every { repositoryNy.updateTrekkFraSkattStatus(any(), any(), any()) } returns Unit

            return behandleTrekkServiceNy
        }
        Given("Trekkdokument dannes") {

            val trekkVersjon = 1
            val trekkFraSkatt = lagTestTrekkFraSkatt(trekkVersjon)
            val alleTrekkSomIkkeErBehandlet = listOf(trekkFraSkatt)
            val betalingsinformasjonForTrekkFraSkatt: BetalingsinformasjonFraSkatt = lagBetalingsinformasjonForTrekkFraSkatt(trekkFraSkatt)

            val perioderForTrekkVersjon = listOf(lagPeriodeForTrekkFraSkatt(trekkFraSkatt, TrekkAlternativ.LOPM, sats = 5000.0))
            val periode = perioderForTrekkVersjon.first()
            val behandleTrekkServiceNy = setUpBehandleTrekkServiceNy(alleTrekkSomIkkeErBehandlet, perioderForTrekkVersjon, emptyList())

            When("Trekkdokument dannes for trekk fra skatt") {
                behandleTrekkServiceNy.behandleTrekk()
                capturedOSDtos shouldHaveSize 1
                val osDto = capturedOSDtos.first()
                osDto.transaksjonID.shouldNotBeEmpty()
                osDto.trekkIDSke shouldBe trekkFraSkatt.trekkid

                val innrapporteringTrekk = osDto.innrapporteringTrekk
                Then("Skal all informasjon være med") {
                    with(innrapporteringTrekk) {
                        aksjonskode shouldBe Aksjonskode.NY
                        kreditorTrekkId shouldBe "${trekkFraSkatt.trekkid}${kodeTrekkAlternativ.value}"
                        kodeTrekkAlternativ shouldBe periode.trekkAlternativ()

                        gyldigTomDato.shouldBeNull()
                        perioder!!.periode.size shouldBe 1
                        debitorId shouldBe trekkFraSkatt.skyldner
                        kid shouldBe betalingsinformasjonForTrekkFraSkatt.kidnummer
                        kreditorsRef shouldBe trekkFraSkatt.saksnummer
                        saldo shouldBe 0.0
                        navTrekkId.shouldBeEmpty()

                        prioritetFomDato shouldBe null
                        perioder.periode.first().periodeFomDato shouldBe periode.startdato
                        perioder.periode.first().periodeTomDato shouldBe periode.sluttdato
                    }
                }

                Then("Skal datoer formatteres på yyyy-mm-dd format") {
                    val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                    innrapporteringTrekk.perioder!!.periode.forEach {
                        LocalDate.parse(it.periodeTomDato, dateFormatter).format(dateFormatter) shouldBe it.periodeTomDato
                        LocalDate.parse(it.periodeFomDato, dateFormatter).format(dateFormatter) shouldBe it.periodeFomDato
                    }
                }

                Then("Skal kodeTrekkType være $KODE_TREKKTYPE") {
                    innrapporteringTrekk.kodeTrekktype shouldBe KODE_TREKKTYPE
                }

                Then("Skal kilde være $KILDE") {
                    innrapporteringTrekk.kilde shouldBe KILDE
                }
            }
        }

        Given("Et mottatt trekk har én periode") {
            And("Perioden har kun trekkalternativ LOPP") {
                When("Trekket har trekkversjon 1") {
                    val trekkVersjon = 1
                    val trekkFraSkatt = lagTestTrekkFraSkatt(trekkVersjon)
                    val alleTrekkSomIkkeErBehandlet = listOf(trekkFraSkatt)

                    Then("Skal trekket bli til 1 NYTT trekk med 1 periode") {
                        val perioderForTrekkVersjon = lagPerioderForSkattLOPP(trekkFraSkatt).take(1)
                        val behandleTrekkServiceNy = setUpBehandleTrekkServiceNy(alleTrekkSomIkkeErBehandlet, perioderForTrekkVersjon, emptyList())
                        behandleTrekkServiceNy.behandleTrekk()

                        capturedOSDtos shouldHaveSize 1
                        with(capturedOSDtos.first().innrapporteringTrekk) {
                            aksjonskode shouldBe Aksjonskode.NY
                            perioder!!.periode shouldHaveSize 1
                        }
                    }
                }

                When("Trekket har trekkversjon 2") {
                    val trekkVersjon = 2
                    val trekkFraSkatt = lagTestTrekkFraSkatt(trekkVersjon)
                    val alleTrekkSomIkkeErBehandlet = listOf(trekkFraSkatt)
                    val perioderForTrekkVersjon = lagPerioderForSkattLOPP(trekkFraSkatt).take(1)

                    And("Vi ikke har fått trekkversjon 1") {
                        val behandleTrekkServiceNy = setUpBehandleTrekkServiceNy(alleTrekkSomIkkeErBehandlet, perioderForTrekkVersjon, emptyList())
                        behandleTrekkServiceNy.behandleTrekk()

                        Then("Skal trekket bli til 1 NYTT trekk med 1 perioder") {
                            capturedOSDtos shouldHaveSize 1
                            with(capturedOSDtos.first().innrapporteringTrekk) {
                                aksjonskode shouldBe Aksjonskode.NY
                                perioder!!.periode shouldHaveSize 1
                            }
                        }
                    }

                    And("Vi har fått trekkversjon 1") {
                        And("trekkalternativ i versjon 1 er LOPP") {
                            val behandleTrekkServiceNy =
                                setUpBehandleTrekkServiceNy(
                                    alleTrekkSomIkkeErBehandlet,
                                    perioderForTrekkVersjon,
                                    kjenteLOPPPerioder = perioderForTrekkVersjon.toKnownPeriods(),
                                )

                            behandleTrekkServiceNy.behandleTrekk()
                            Then("Skal trekket bli til 1 ENDRET trekk met 1 periode") {
                                capturedOSDtos shouldHaveSize 1

                                with(capturedOSDtos.first().innrapporteringTrekk) {
                                    aksjonskode shouldBe Aksjonskode.ENDR
                                    perioder!!.periode shouldHaveSize 1
                                }
                            }
                        }

                        And("trekkalternativ i versjon 1 er LOPM") {
                            val kjenteLopmPerioder = lagPerioderForSkattLOPM(trekkFraSkatt).toKnownPeriods()
                            val behandleTrekkServiceNy =
                                setUpBehandleTrekkServiceNy(
                                    alleTrekkSomIkkeErBehandlet,
                                    perioderForTrekkVersjon,
                                    kjenteLOPMPerioder = kjenteLopmPerioder,
                                )

                            behandleTrekkServiceNy.behandleTrekk()
                            capturedOSDtos shouldHaveSize 2

                            Then("Skal 1. trekk bli til 1 NYTT trekk med 1 periode og trekkalternativ LOPP") {
                                with(capturedOSDtos.first().innrapporteringTrekk) {
                                    aksjonskode shouldBe Aksjonskode.NY
                                    kodeTrekkAlternativ shouldBe TrekkAlternativ.LOPP
                                    perioder!!.periode shouldHaveSize 1
                                    perioder.periode.first().sats shouldNotBe 0.0
                                }
                            }

                            Then("Skal 2. trekk bli til 1 ENDRET trekk med 1 periode med sats 0.0 og trekkalternativ LOPM") {
                                with(capturedOSDtos.last().innrapporteringTrekk) {
                                    aksjonskode shouldBe Aksjonskode.ENDR
                                    kodeTrekkAlternativ shouldBe TrekkAlternativ.LOPM
                                    perioder!!.periode shouldHaveSize 1
                                    perioder.periode.first().sats shouldBe 0.0
                                }
                            }
                        }

                        And("trekkstatus er AVSLUTTET") {
                            val avsluttetTrekkFraSkatt = trekkFraSkatt.copy(trekkstatus = Trekkstatus.AVSLUTTET.name)
                            val alleAvsluttetTrekkSomIkkeErSendt = listOf(avsluttetTrekkFraSkatt)
                            And("Trekket har ingen periode") {
                                val behandleTrekkServiceNy =
                                    setUpBehandleTrekkServiceNy(
                                        alleTrekkSomIkkeErBehandlet = alleAvsluttetTrekkSomIkkeErSendt,
                                        perioderForTrekkFraSkatt = emptyList(),
                                        kjenteLOPPPerioder = perioderForTrekkVersjon.toKnownPeriods(),
                                    )
                                Then("Skal 1 trekk bli til 1 ENDRET trekk med ingen periode og gyldigTomDate dagens -1") {
                                    behandleTrekkServiceNy.behandleTrekk()
                                    capturedOSDtos shouldHaveSize 1

                                    with(capturedOSDtos.first().innrapporteringTrekk) {
                                        aksjonskode shouldBe Aksjonskode.ENDR
                                        gyldigTomDato shouldBe gyldigTomDatoAvslutt
                                        perioder.shouldBeNull()
                                    }
                                }
                            }

                            And("Trekket er 1 periode") {
                                val behandleTrekkServiceNy =
                                    setUpBehandleTrekkServiceNy(
                                        alleAvsluttetTrekkSomIkkeErSendt,
                                        perioderForTrekkVersjon,
                                        kjenteLOPPPerioder = perioderForTrekkVersjon.toKnownPeriods(),
                                    )
                                Then("Skal 1 trekk bli til 1 ENDRET trekk uten perioder og gyldigTomDato dagens -1") {
                                    behandleTrekkServiceNy.behandleTrekk()
                                    capturedOSDtos shouldHaveSize 1

                                    with(capturedOSDtos.first().innrapporteringTrekk) {
                                        aksjonskode shouldBe Aksjonskode.ENDR
                                        gyldigTomDato shouldBe gyldigTomDatoAvslutt
                                        perioder.shouldBeNull()
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
                    val alleTrekkSomIkkeErBehandlet = listOf(trekkFraSkatt)

                    Then("Skal trekket bli til 1 NYTT trekk med 1 periode") {
                        val perioderForTrekkVersjon = lagPerioderForSkattLOPM(trekkFraSkatt).take(1)
                        val behandleTrekkServiceNy = setUpBehandleTrekkServiceNy(alleTrekkSomIkkeErBehandlet, perioderForTrekkVersjon, emptyList())
                        behandleTrekkServiceNy.behandleTrekk()

                        capturedOSDtos shouldHaveSize 1
                        with(capturedOSDtos.first().innrapporteringTrekk) {
                            aksjonskode shouldBe Aksjonskode.NY
                            perioder!!.periode shouldHaveSize 1
                        }
                    }
                }

                When("Trekket har trekkversjon 2") {
                    val trekkVersjon = 2
                    val trekkFraSkatt = lagTestTrekkFraSkatt(trekkVersjon)
                    val alleTrekkSomIkkeErBehandlet = listOf(trekkFraSkatt)
                    val perioderForTrekkVersjon = lagPerioderForSkattLOPM(trekkFraSkatt).take(1)

                    And("Vi ikke har fått trekkversjon 1") {
                        val behandleTrekkServiceNy = setUpBehandleTrekkServiceNy(alleTrekkSomIkkeErBehandlet, perioderForTrekkVersjon, emptyList())
                        behandleTrekkServiceNy.behandleTrekk()

                        Then("Skal trekket bli til 1 NYTT trekk med 1 perioder") {
                            capturedOSDtos shouldHaveSize 1
                            with(capturedOSDtos.first().innrapporteringTrekk) {
                                aksjonskode shouldBe Aksjonskode.NY
                                perioder!!.periode shouldHaveSize 1
                            }
                        }
                    }

                    And("Vi har fått trekkversjon 1") {
                        And("trekkalternativ i versjon 1 er LOPP") {
                            val kjenteLoppPerioder = lagPerioderForSkattLOPP(trekkFraSkatt).toKnownPeriods()
                            val behandleTrekkServiceNy =
                                setUpBehandleTrekkServiceNy(
                                    alleTrekkSomIkkeErBehandlet,
                                    perioderForTrekkVersjon,
                                    kjenteLOPPPerioder = kjenteLoppPerioder,
                                )

                            behandleTrekkServiceNy.behandleTrekk()
                            capturedOSDtos shouldHaveSize 2
                            Then("Skal 1. trekk bli til 1 NYTT trekk med 1 periode og trekkalternativ LOPM") {
                                with(capturedOSDtos.first().innrapporteringTrekk) {
                                    aksjonskode shouldBe Aksjonskode.NY
                                    kodeTrekkAlternativ shouldBe TrekkAlternativ.LOPM
                                    perioder!!.periode shouldHaveSize 1
                                    perioder.periode.first().sats shouldNotBe 0.0
                                }
                            }

                            Then("Skal 1. trekk bli til 1 ENDRET trekk med 1 periode med sats 0.0 og trekkalternativ LOPP") {
                                with(capturedOSDtos.last().innrapporteringTrekk) {
                                    aksjonskode shouldBe Aksjonskode.ENDR
                                    kodeTrekkAlternativ shouldBe TrekkAlternativ.LOPP
                                    perioder!!.periode shouldHaveSize 1
                                    perioder.periode.first().sats shouldBe 0.0
                                }
                            }
                        }

                        And("trekkalternativ i versjon 1 er LOPM") {
                            val behandleTrekkServiceNy =
                                setUpBehandleTrekkServiceNy(
                                    alleTrekkSomIkkeErBehandlet,
                                    perioderForTrekkVersjon,
                                    kjenteLOPMPerioder = perioderForTrekkVersjon.toKnownPeriods(),
                                )

                            Then("Skal trekket bli til 1 ENDRET trekk met 1 periode") {
                                behandleTrekkServiceNy.behandleTrekk()
                                capturedOSDtos shouldHaveSize 1
                                with(capturedOSDtos.first().innrapporteringTrekk) {
                                    aksjonskode shouldBe Aksjonskode.ENDR
                                    perioder!!.periode shouldHaveSize 1
                                    perioder.periode.first().sats shouldNotBe 0.0
                                }
                            }
                        }

                        And("trekkstatus er AVSLUTTET") {
                            val avsluttetTrekkFraSkatt = trekkFraSkatt.copy(trekkstatus = Trekkstatus.AVSLUTTET.name)
                            val alleAvsluttetTrekkSomIkkeErSendt = listOf(avsluttetTrekkFraSkatt)
                            And("Trekket har ingen periode") {
                                val behandleTrekkServiceNy =
                                    setUpBehandleTrekkServiceNy(
                                        alleTrekkSomIkkeErBehandlet = alleAvsluttetTrekkSomIkkeErSendt,
                                        perioderForTrekkFraSkatt = emptyList(),
                                        kjenteLOPMPerioder = perioderForTrekkVersjon.toKnownPeriods(),
                                    )
                                Then("Skal 1 trekk bli til 1 ENDRET trekk med ingen periode og gyldigTomDate dagens -1") {
                                    behandleTrekkServiceNy.behandleTrekk()
                                    capturedOSDtos shouldHaveSize 1

                                    with(capturedOSDtos.first().innrapporteringTrekk) {
                                        aksjonskode shouldBe Aksjonskode.ENDR
                                        gyldigTomDato shouldBe gyldigTomDatoAvslutt
                                        perioder.shouldBeNull()
                                    }
                                }
                            }

                            And("Trekket er 1 periode") {
                                val behandleTrekkServiceNy =
                                    setUpBehandleTrekkServiceNy(
                                        alleAvsluttetTrekkSomIkkeErSendt,
                                        perioderForTrekkVersjon,
                                        kjenteLOPMPerioder = perioderForTrekkVersjon.toKnownPeriods(),
                                    )
                                Then("Skal 1 trekk bli til 1 ENDRET trekk uten perioder og gyldigTomDato dagens -1") {
                                    behandleTrekkServiceNy.behandleTrekk()
                                    capturedOSDtos shouldHaveSize 1

                                    with(capturedOSDtos.first().innrapporteringTrekk) {
                                        aksjonskode shouldBe Aksjonskode.ENDR
                                        gyldigTomDato shouldBe gyldigTomDatoAvslutt
                                        perioder.shouldBeNull()
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
                val trekkVersjon = 1
                val trekkFraSkatt = lagTestTrekkFraSkatt(trekkVersjon)
                val alleTrekkSomIkkeErBehandlet = listOf(trekkFraSkatt)

                And("Periodene i trekket har aksjonskodene LOPM og LOPP") {
                    val perioderForTrekkVersjon = lagPerioderForSkattLOPP(trekkFraSkatt).take(2) + lagPerioderForSkattLOPM(trekkFraSkatt).drop(2)
                    val behandleTrekkServiceNy = setUpBehandleTrekkServiceNy(alleTrekkSomIkkeErBehandlet, perioderForTrekkVersjon, emptyList())
                    behandleTrekkServiceNy.behandleTrekk()

                    Then("Skal trekket bli til 2 NYE trekk") {
                        capturedOSDtos shouldHaveSize 2

                        val trekkDokument1 = capturedOSDtos.first()
                        trekkDokument1.innrapporteringTrekk.aksjonskode shouldBe Aksjonskode.NY
                        val dokument1Periodene = trekkDokument1.innrapporteringTrekk.perioder!!.periode
                        dokument1Periodene shouldHaveSize 3
                        dokument1Periodene[0].sats shouldBe 20.0
                        dokument1Periodene[1].sats shouldBe 15.0
                        dokument1Periodene[2].sats shouldBe 0.0

                        val trekkDokument2 = capturedOSDtos.last()
                        trekkDokument2.innrapporteringTrekk.aksjonskode shouldBe Aksjonskode.NY
                        val dokument2Periodene = trekkDokument2.innrapporteringTrekk.perioder!!.periode
                        dokument2Periodene shouldHaveSize 3
                        dokument2Periodene[0].sats shouldBe 0.0
                        dokument2Periodene[1].sats shouldBe 0.0
                        dokument2Periodene[2].sats shouldBe 1000.0
                    }
                }
            }
            And("Trekket har trekkversjon 2") {
                val trekkVersjon = 2
                val trekkFraSkatt = lagTestTrekkFraSkatt(trekkVersjon)
                val perioderForSkattLOPM = lagPerioderForSkattLOPM(trekkFraSkatt)
                val perioderForSkattLOPP = lagPerioderForSkattLOPP(trekkFraSkatt)

                And("Versjon 2 har trekkstatus AKTIV") {
                    val alleTrekkSomIkkeErBehandlet = listOf(trekkFraSkatt)
                    And("Periodene i trekkversjon 1 har trekkalternativ LOPM") {
                        And("Periodene i versjon 2 har aksjonskodene LOPM og LOPP") {
                            val perioderForTrekkVersjon = perioderForSkattLOPM.take(1) + perioderForSkattLOPP.drop(1)
                            val behandleTrekkServiceNy =
                                setUpBehandleTrekkServiceNy(
                                    alleTrekkSomIkkeErBehandlet,
                                    perioderForTrekkVersjon,
                                    kjenteLOPMPerioder = perioderForSkattLOPM.toKnownPeriods(),
                                )

                            behandleTrekkServiceNy.behandleTrekk()
                            capturedOSDtos shouldHaveSize 2
                            Then("Skal 1. trekk bli til 1 ENDRET trekk med trekkalternative LOPM og 3 perioder") {
                                with(capturedOSDtos.first().innrapporteringTrekk) {
                                    aksjonskode shouldBe Aksjonskode.ENDR
                                    kodeTrekkAlternativ shouldBe TrekkAlternativ.LOPM

                                    val periodene = perioder!!.periode
                                    periodene shouldHaveSize 3
                                    periodene[0].sats shouldBe 3000.0
                                    periodene[1].sats shouldBe 0.0
                                    periodene[2].sats shouldBe 0.0
                                }
                            }
                            Then("Skal 2. trekk bli til 1 NYTT trekk med trekkalternative LOPP og 3 perioder") {
                                with(capturedOSDtos.last().innrapporteringTrekk) {
                                    aksjonskode shouldBe Aksjonskode.NY
                                    kodeTrekkAlternativ shouldBe TrekkAlternativ.LOPP

                                    val periodene = perioder!!.periode
                                    periodene shouldHaveSize 3
                                    periodene[0].sats shouldBe 0.0
                                    periodene[1].sats shouldBe 15.0
                                    periodene[2].sats shouldBe 10.0
                                }
                            }
                        }
                        And("Periodene i versjon 2 har kun aksjonskode LOPP") {
                            val behandleTrekkServiceNy =
                                setUpBehandleTrekkServiceNy(
                                    alleTrekkSomIkkeErBehandlet,
                                    perioderForSkattLOPP,
                                    kjenteLOPMPerioder = perioderForSkattLOPM.toKnownPeriods(),
                                )

                            behandleTrekkServiceNy.behandleTrekk()
                            capturedOSDtos shouldHaveSize 2
                            Then("Skal 1. trekk bli til 1 NYTT trekk med trekkalternative LOPP og 3 perioder") {
                                with(capturedOSDtos.first().innrapporteringTrekk) {
                                    aksjonskode shouldBe Aksjonskode.NY
                                    kodeTrekkAlternativ shouldBe TrekkAlternativ.LOPP

                                    val periodene = perioder!!.periode
                                    periodene shouldHaveSize 3
                                    periodene[0].sats shouldBe 20.0
                                    periodene[1].sats shouldBe 15.0
                                    periodene[2].sats shouldBe 10.0
                                }
                            }

                            Then("Skal 2. trekk bli til 1 ENDRET trekk med trekkalternative LOPM, 3 perioder med sats 0.0") {
                                with(capturedOSDtos.last().innrapporteringTrekk) {
                                    aksjonskode shouldBe Aksjonskode.ENDR
                                    kodeTrekkAlternativ shouldBe TrekkAlternativ.LOPM

                                    perioder!!.periode shouldHaveSize 3
                                    perioder.periode.forEach { it.sats shouldBe 0.0 }
                                }
                            }
                        }
                        And("Periodene i versjon 2 har kun aksjonkode LOPM") {
                            val behandleTrekkServiceNy =
                                setUpBehandleTrekkServiceNy(
                                    alleTrekkSomIkkeErBehandlet,
                                    perioderForSkattLOPM,
                                    kjenteLOPMPerioder = perioderForSkattLOPM.toKnownPeriods(),
                                )

                            behandleTrekkServiceNy.behandleTrekk()
                            capturedOSDtos shouldHaveSize 1
                            Then("Skal 1 trekk bli til 1 ENDRET trekk med trekkalternative LOPM og 3 perioder") {
                                with(capturedOSDtos.first().innrapporteringTrekk) {
                                    aksjonskode shouldBe Aksjonskode.ENDR
                                    kodeTrekkAlternativ shouldBe TrekkAlternativ.LOPM

                                    val periodene = perioder!!.periode
                                    periodene shouldHaveSize 3
                                    periodene[0].sats shouldBe 3000.0
                                    periodene[1].sats shouldBe 2000.0
                                    periodene[2].sats shouldBe 1000.0
                                }
                            }
                        }
                    }
                }
                And("Versjon 2 har trekkstatus AVSLUTTET") {
                    val avsluttetTrekkFraSkatt = trekkFraSkatt.copy(trekkstatus = Trekkstatus.AVSLUTTET.name)
                    val alleAvsluttetTrekkSomIkkeErSent = listOf(avsluttetTrekkFraSkatt)
                    And("Versjon 2 har ingen perioder") {
                        And("Versjon 1 har trekkalternativ LOPP") {
                            val behandleTrekkServiceNy =
                                setUpBehandleTrekkServiceNy(
                                    alleAvsluttetTrekkSomIkkeErSent,
                                    perioderForTrekkFraSkatt = emptyList(),
                                    kjenteLOPPPerioder = perioderForSkattLOPP.toKnownPeriods(),
                                )
                            Then("Skal 1 trekk bli til 1 ENDRET trekk uten perioder og med gyldigTomDato dagens -1") {
                                behandleTrekkServiceNy.behandleTrekk()
                                capturedOSDtos shouldHaveSize 1

                                with(capturedOSDtos.first().innrapporteringTrekk) {
                                    aksjonskode shouldBe Aksjonskode.ENDR
                                    gyldigTomDato shouldBe gyldigTomDatoAvslutt
                                    perioder.shouldBeNull()
                                }
                            }
                        }
                        And("Versjon 1 har trekkalternativ LOPM og LOPP") {
                            val behandleTrekkServiceNy =
                                setUpBehandleTrekkServiceNy(
                                    alleAvsluttetTrekkSomIkkeErSent,
                                    perioderForTrekkFraSkatt = emptyList(),
                                    kjenteLOPPPerioder = perioderForSkattLOPP.toKnownPeriods(),
                                    kjenteLOPMPerioder = perioderForSkattLOPM.toKnownPeriods(),
                                )
                            Then("Skal trekket bli til 2 ENDRET trekk uten perioder og med gyldigTomDato dagens -1") {
                                behandleTrekkServiceNy.behandleTrekk()
                                capturedOSDtos shouldHaveSize 2

                                capturedOSDtos.forEach { (_, _, innrapporteringTrekk, _) ->
                                    innrapporteringTrekk.aksjonskode shouldBe Aksjonskode.ENDR
                                    innrapporteringTrekk.gyldigTomDato shouldBe gyldigTomDatoAvslutt
                                    innrapporteringTrekk.perioder.shouldBeNull()
                                }
                            }
                        }
                    }
                    And("Versjon 2 har 3 perioder") {
                        And("Versjon 1 har trekkalternativ LOPP") {
                            val behandleTrekkServiceNy =
                                setUpBehandleTrekkServiceNy(
                                    alleAvsluttetTrekkSomIkkeErSent,
                                    perioderForSkattLOPP,
                                    kjenteLOPPPerioder = perioderForSkattLOPP.toKnownPeriods(),
                                )
                            behandleTrekkServiceNy.behandleTrekk()
                            Then("Skal 1 trekk bli til 1 ENDRET trekk uten perioder og gyldigTomDato dagens -1") {
                                capturedOSDtos shouldHaveSize 1
                                with(capturedOSDtos.first().innrapporteringTrekk) {
                                    aksjonskode shouldBe Aksjonskode.ENDR
                                    perioder.shouldBeNull()
                                }
                            }
                        }
                        And("Versjon 1 har trekkalternativ LOPM og LOPP") {
                            val behandleTrekkServiceNy =
                                setUpBehandleTrekkServiceNy(
                                    alleAvsluttetTrekkSomIkkeErSent,
                                    perioderForSkattLOPP,
                                    perioderForSkattLOPP.toKnownPeriods(),
                                    perioderForSkattLOPM.toKnownPeriods(),
                                )

                            behandleTrekkServiceNy.behandleTrekk()
                            Then("Skal trekket bli til 2 ENDRET trekk uten perioder og gyldigTomDato dagens -1") {
                                capturedOSDtos shouldHaveSize 2

                                capturedOSDtos.forEach { oSDto ->
                                    with(oSDto.innrapporteringTrekk) {
                                        aksjonskode shouldBe Aksjonskode.ENDR
                                        gyldigTomDato shouldBe gyldigTomDatoAvslutt
                                        perioder.shouldBeNull()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    })