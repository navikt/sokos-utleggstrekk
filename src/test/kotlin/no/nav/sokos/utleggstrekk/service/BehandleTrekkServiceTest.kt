package no.nav.sokos.utleggstrekk.service

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldBeEmpty
import io.kotest.matchers.string.shouldNotBeEmpty
import io.ktor.server.config.ApplicationConfig
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.invoke
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import kotliquery.TransactionalSession

import no.nav.sokos.utleggstrekk.config.PropertiesConfig
import no.nav.sokos.utleggstrekk.database.Repository
import no.nav.sokos.utleggstrekk.database.model.BetalingsinformasjonFraSkatt
import no.nav.sokos.utleggstrekk.database.model.PeriodeFraSkatt
import no.nav.sokos.utleggstrekk.database.model.PeriodeTilOS
import no.nav.sokos.utleggstrekk.database.model.SkattTrekkStatus
import no.nav.sokos.utleggstrekk.database.model.TrekkFraSkatt
import no.nav.sokos.utleggstrekk.database.model.mapNewFomTom
import no.nav.sokos.utleggstrekk.domene.nav.Aksjonskode
import no.nav.sokos.utleggstrekk.domene.nav.OSDto
import no.nav.sokos.utleggstrekk.domene.nav.TrekkAlternativ
import no.nav.sokos.utleggstrekk.domene.ske.Trekkstatus
import no.nav.sokos.utleggstrekk.util.asDate
import no.nav.sokos.utleggstrekk.util.dager
import no.nav.sokos.utleggstrekk.util.idag
import no.nav.sokos.utleggstrekk.util.mnd
import no.nav.sokos.utleggstrekk.util.plus

class BehandleTrekkServiceTest :
    BehaviorSpec({
        val capturedOSDtos = mutableListOf<OSDto>()

        beforeSpec {
            mockkObject(PropertiesConfig)
            every { PropertiesConfig.config } returns ApplicationConfig("application-test.conf")
        }

        afterContainer {
            capturedOSDtos.clear()
            clearAllMocks()
        }

        afterSpec {
            unmockkObject(PropertiesConfig)
        }

        val gyldigTomDatoAvslutt = LocalDate.now().toString()

        fun setUpBehandleTrekkServiceNy(
            alleTrekkSomIkkeErBehandlet: List<TrekkFraSkatt>,
            perioderForTrekkFraSkatt: List<PeriodeFraSkatt>,
            kjenteLOPPPerioder: List<PeriodeTilOS> = emptyList(),
            kjenteLOPMPerioder: List<PeriodeTilOS> = emptyList(),
        ): BehandleTrekkService {
            val betalingsinformasjonForTrekkFraSkatt: BetalingsinformasjonFraSkatt = lagBetalingsinformasjonForTrekkFraSkatt(alleTrekkSomIkkeErBehandlet.first())

            val repository = mockk<Repository>()
            val behandleTrekkService = BehandleTrekkService(repository)
            val trekkAlternativIOS =
                buildSet {
                    if (kjenteLOPMPerioder.isNotEmpty()) add(TrekkAlternativ.LOPM)
                    if (kjenteLOPPPerioder.isNotEmpty()) add(TrekkAlternativ.LOPP)
                }

            // Mock withTransaction
            every { repository.withTransaction<Any?>(captureLambda()) } answers {
                // Execute the captured lambda with a session mock
                val session = mockk<TransactionalSession>()
                lambda<(TransactionalSession) -> Any?>().invoke(session)
            }

            every { repository.getOsAlternativForTrekk(any(), any()) } returns trekkAlternativIOS
            every { repository.getPerioderTilOs(any(), TrekkAlternativ.LOPM) } returns kjenteLOPMPerioder
            every { repository.getPerioderTilOs(any(), TrekkAlternativ.LOPP) } returns kjenteLOPPPerioder
            every { repository.getTrekkIdTilTrekkSomSkalBehandles() } returns alleTrekkSomIkkeErBehandlet.map { it.id }
            val trekkIdSlot = slot<Long>()
            every { repository.getTrekkFraSkatt(capture(trekkIdSlot), any()) } answers {
                alleTrekkSomIkkeErBehandlet.first { it.id == trekkIdSlot.captured }
            }
            every { repository.getSkattTrekkStatus(capture(trekkIdSlot), any()) } answers {
                SkattTrekkStatus.MOTTATT
            }
            every { repository.getPerioderForTrekk(any()) } returns perioderForTrekkFraSkatt
            every { repository.getBetalingsinformasjonForTrekk(any()) } returns betalingsinformasjonForTrekkFraSkatt
            every { repository.insertTransaksjonTilOs(capture(capturedOSDtos), any()) } returns Unit
            every { repository.updateTrekkFraSkattStatus(any(), any(), any()) } returns Unit

            return behandleTrekkService
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
                        with(perioder.periode.first()) {
                            val periodeFomAsDate = periodeFomDato.asDate
                            val startDatoAsDate = periode.startdato.asDate

                            periodeFomAsDate.year shouldBe startDatoAsDate.year
                            periodeFomAsDate.monthValue shouldBe startDatoAsDate.monthValue
                            periodeFomAsDate.dayOfMonth shouldBe 1

                            periode.sluttdato?.let {
                                periodeTomDato.shouldNotBeNull()
                                val periodeTomAsDate = periodeTomDato.asDate
                                val sluttDatoAsDate = periode.sluttdato.asDate

                                periodeTomAsDate.year shouldBe sluttDatoAsDate.year
                                periodeTomAsDate.monthValue shouldBe sluttDatoAsDate.monthValue
                                periodeTomAsDate.dayOfMonth shouldBe sluttDatoAsDate.lengthOfMonth()
                            }
                        }
                    }
                }

                Then("Skal datoer formatteres på yyyy-mm-dd format") {
                    val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                    innrapporteringTrekk.perioder!!.periode.forEach {
                        if (it.periodeTomDato != null) LocalDate.parse(it.periodeTomDato, dateFormatter).format(dateFormatter) shouldBe it.periodeTomDato
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
                            Then("Skal trekket bli til 1 ENDRET trekk uten perioder") {
                                capturedOSDtos shouldHaveSize 1

                                with(capturedOSDtos.first().innrapporteringTrekk) {
                                    aksjonskode shouldBe Aksjonskode.ENDR
                                    perioder shouldBe null
                                }
                            }
                        }

                        And("trekkalternativ i versjon 1 er LOPM") {
                            val kjenteLopmPerioder = lagPerioderForSkattLOPM(trekkFraSkatt).toKnownPeriods().take(1)
                            val behandleTrekkServiceNy =
                                setUpBehandleTrekkServiceNy(
                                    alleTrekkSomIkkeErBehandlet,
                                    perioderForTrekkVersjon,
                                    kjenteLOPMPerioder = kjenteLopmPerioder,
                                )

                            behandleTrekkServiceNy.behandleTrekk()
                            capturedOSDtos shouldHaveSize 2

                            Then("Skal første trekk bli til 1 NYTT trekk med 1 periode og trekkalternativ LOPP") {
                                with(capturedOSDtos.first().innrapporteringTrekk) {
                                    aksjonskode shouldBe Aksjonskode.NY
                                    kodeTrekkAlternativ shouldBe TrekkAlternativ.LOPP
                                    perioder!!.periode shouldHaveSize 1
                                    perioder.periode.first().sats shouldNotBe 0.0
                                }
                            }
                            Then("Skal andre trekk bli til 1 ENDRET trekk med 2 perioder med sats 0.0 og trekkalternativ LOPM") {
                                with(capturedOSDtos.last().innrapporteringTrekk) {
                                    aksjonskode shouldBe Aksjonskode.ENDR
                                    kodeTrekkAlternativ shouldBe TrekkAlternativ.LOPM
                                    perioder!!.periode shouldHaveSize 2
                                    perioder.periode.first().sats shouldBe 0.0
                                    perioder.periode.last().sats shouldBe 0.0
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
                    val perioderForTrekkVersjon = lagPerioderForSkattLOPM(trekkFraSkatt).drop(1).take(1)

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
                            val kjenteLoppPerioder = lagPerioderForSkattLOPP(trekkFraSkatt).toKnownPeriods().take(1)
                            val behandleTrekkServiceNy =
                                setUpBehandleTrekkServiceNy(
                                    alleTrekkSomIkkeErBehandlet,
                                    lagPerioderForSkattLOPP(trekkFraSkatt).take(1) + perioderForTrekkVersjon,
                                    kjenteLOPPPerioder = kjenteLoppPerioder,
                                )

                            behandleTrekkServiceNy.behandleTrekk()
                            capturedOSDtos shouldHaveSize 2
                            Then("Skal første trekk bli til 1 NYTT trekk med 1 periode og trekkalternativ LOPM") {
                                with(capturedOSDtos.last().innrapporteringTrekk) {
                                    aksjonskode shouldBe Aksjonskode.NY
                                    kodeTrekkAlternativ shouldBe TrekkAlternativ.LOPM
                                    perioder!!.periode shouldHaveSize 1
                                    perioder.periode.first().sats shouldNotBe 0.0
                                }
                            }

                            Then("Skal andre trekk bli til 1 ENDRET trekk med 1 periode med sats 0.0 og trekkalternativ LOPP") {
                                with(capturedOSDtos.first().innrapporteringTrekk) {
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
                                    lagPerioderForSkattLOPM(trekkFraSkatt).take(2),
                                    kjenteLOPMPerioder = perioderForTrekkVersjon.toKnownPeriods(),
                                )

                            Then("Skal trekket bli til 1 ENDRET trekk med 1 periode") {
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

            val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE).asDate

            And("Sluttdato ikke er siste dato i måneden") {
                val trekkVersjon = 1
                val trekkFraSkatt = lagTestTrekkFraSkatt(trekkVersjon)
                val alleTrekkSomIkkeErBehandlet = listOf(trekkFraSkatt)
                val dateWithWrongSluttDato = if (today.dayOfMonth == today.lengthOfMonth()) today.minusDays(1) else today
                val perioderForTrekk = lagPeriodeForTrekkFraSkatt(trekkFraSkatt, sluttDato = dateWithWrongSluttDato.toString())

                Then("Skal tom dato settes til siste dato i måneden") {

                    val behandleTrekkServiceNy = setUpBehandleTrekkServiceNy(alleTrekkSomIkkeErBehandlet, listOf(perioderForTrekk), emptyList())
                    behandleTrekkServiceNy.behandleTrekk()

                    capturedOSDtos shouldHaveSize 1
                    with(capturedOSDtos.first().innrapporteringTrekk) {
                        perioder!!.periode shouldHaveSize 1
                        val periodeTom = perioder.periode.first().periodeTomDato!!
                        LocalDate.parse(periodeTom).dayOfMonth shouldBe dateWithWrongSluttDato.lengthOfMonth()
                    }
                }
            }
            And("Perioden ikke har sluttdato") {
                Then("Skal ikke tom-dato settes") {
                    val trekkVersjon = 1
                    val trekkFraSkatt = lagTestTrekkFraSkatt(trekkVersjon)
                    val alleTrekkSomIkkeErBehandlet = listOf(trekkFraSkatt)

                    val perioderForTrekk = lagPeriodeForTrekkFraSkatt(trekkFraSkatt, sluttDato = null)

                    val behandleTrekkServiceNy = setUpBehandleTrekkServiceNy(alleTrekkSomIkkeErBehandlet, listOf(perioderForTrekk), emptyList())
                    behandleTrekkServiceNy.behandleTrekk()

                    capturedOSDtos shouldHaveSize 1
                    with(capturedOSDtos.first().innrapporteringTrekk) {
                        perioder!!.periode shouldHaveSize 1
                        perioder.periode.first().periodeTomDato shouldBe null
                    }
                }
            }
            And("Perioden startdato ikke er første dato i måneden") {
                val trekkVersjon = 1
                val trekkFraSkatt = lagTestTrekkFraSkatt(trekkVersjon)
                val alleTrekkSomIkkeErBehandlet = listOf(trekkFraSkatt)
                val dateWithWrongStartDato = if (today.dayOfMonth == 1) today.plusDays(1) else today
                val perioderForTrekk = lagPeriodeForTrekkFraSkatt(trekkFraSkatt, startDato = dateWithWrongStartDato.toString())

                Then("Skal fom-dato settes til den første i måneden") {

                    val behandleTrekkServiceNy = setUpBehandleTrekkServiceNy(alleTrekkSomIkkeErBehandlet, listOf(perioderForTrekk), emptyList())
                    behandleTrekkServiceNy.behandleTrekk()

                    capturedOSDtos shouldHaveSize 1
                    with(capturedOSDtos.first().innrapporteringTrekk) {
                        perioder!!.periode shouldHaveSize 1
                        val periodeFom = perioder.periode.first().periodeFomDato
                        LocalDate.parse(periodeFom).dayOfMonth shouldBe 1
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

                            val perioderForTrekkVersjon = perioderForSkattLOPM.drop(1).take(1) + perioderForSkattLOPP.drop(2).take(1)
                            val behandleTrekkServiceNy =
                                setUpBehandleTrekkServiceNy(
                                    alleTrekkSomIkkeErBehandlet,
                                    perioderForTrekkVersjon,
                                    kjenteLOPMPerioder = perioderForSkattLOPM.take(1).toKnownPeriods(),
                                )

                            behandleTrekkServiceNy.behandleTrekk()
                            capturedOSDtos shouldHaveSize 2
                            Then("Skal første trekk bli til 1 ENDRET trekk med trekkalternativ LOPM og 3 perioder") {
                                with(capturedOSDtos.find { it.innrapporteringTrekk.aksjonskode == Aksjonskode.ENDR }!!.innrapporteringTrekk) {
                                    aksjonskode shouldBe Aksjonskode.ENDR
                                    kodeTrekkAlternativ shouldBe TrekkAlternativ.LOPM
                                    val periodene = perioder!!.periode
                                    periodene shouldHaveSize 3
                                    periodene[0].sats shouldBe 0.0
                                    periodene[1].sats shouldBe 2000.0
                                    periodene[2].sats shouldBe 0.0
                                }
                            }
                            Then("Skal andre trekk bli til 1 NYTT trekk med trekkalternative LOPP og 2 perioder") {
                                with(capturedOSDtos.find { it.innrapporteringTrekk.aksjonskode == Aksjonskode.NY }!!.innrapporteringTrekk) {
                                    aksjonskode shouldBe Aksjonskode.NY
                                    kodeTrekkAlternativ shouldBe TrekkAlternativ.LOPP
                                    val periodene = perioder!!.periode
                                    periodene shouldHaveSize 2
                                    periodene[0].sats shouldBe 0.0
                                    periodene[1].sats shouldBe 10.0
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
                            Then("Skal første trekk bli NYTT trekk med trekkalternative LOPP og 3 perioder") {
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

                            Then("Skal andre trekk bli ENDRET trekk med trekkalternativet LOPM, 6 perioder med sats 0.0") {
                                with(capturedOSDtos.last().innrapporteringTrekk) {
                                    aksjonskode shouldBe Aksjonskode.ENDR
                                    kodeTrekkAlternativ shouldBe TrekkAlternativ.LOPM

                                    perioder!!.periode shouldHaveSize 6
                                    perioder.periode.forEach { it.sats shouldBe 0.0 }
                                }
                            }
                        }
                        And("Periodene i versjon 2 har kun aksjonkode LOPM") {
                            val behandleTrekkServiceNy =
                                setUpBehandleTrekkServiceNy(
                                    alleTrekkSomIkkeErBehandlet,
                                    perioderForSkattLOPM,
                                    kjenteLOPMPerioder = perioderForSkattLOPM.toKnownPeriods().take(2),
                                )

                            behandleTrekkServiceNy.behandleTrekk()
                            capturedOSDtos shouldHaveSize 1
                            Then("Skal 1 trekk bli til 1 ENDRET trekk med trekkalternativ LOPM og 1 perioder") {
                                with(capturedOSDtos.first().innrapporteringTrekk) {
                                    aksjonskode shouldBe Aksjonskode.ENDR
                                    kodeTrekkAlternativ shouldBe TrekkAlternativ.LOPM

                                    val periodene = perioder!!.periode
                                    periodene shouldHaveSize 1
                                    periodene[0].sats shouldBe 1000.0
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

                                capturedOSDtos.forEach { (_, _, _, innrapporteringTrekk, _) ->
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
                            Then("Skal trekket bli til 2 ENDRET trekk uten perioder og gyldigTomDato skal settes til i dag") {
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

private fun lagTestTrekkFraSkatt(trekkVersjon: Int = 1): TrekkFraSkatt {
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

private fun lagPeriodeForTrekkFraSkatt(
    trekkFraSkatt: TrekkFraSkatt,
    trekkAlternativ: TrekkAlternativ = TrekkAlternativ.LOPM,
    startDato: String = idag,
    sluttDato: String? = idag plus 3.mnd, // "2025-03-31",
    sats: Double = 3000.0,
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

private fun lagPerioderForSkattLOPP(trekkFraSkatt: TrekkFraSkatt): List<PeriodeFraSkatt> {
    val perioderEn = lagPeriodeForTrekkFraSkatt(trekkFraSkatt, TrekkAlternativ.LOPP, idag, idag plus 3.mnd, 20.0)
    val perioderTo = lagPeriodeForTrekkFraSkatt(trekkFraSkatt, TrekkAlternativ.LOPP, idag plus 3.mnd plus 1.dager, idag plus 5.mnd, 15.0)
    val perioderTre = lagPeriodeForTrekkFraSkatt(trekkFraSkatt, TrekkAlternativ.LOPP, idag plus 5.mnd plus 1.dager, idag plus 7.mnd, 10.0)

    return listOf(perioderEn, perioderTo, perioderTre).mapNewFomTom()
}

private fun lagPerioderForSkattLOPM(trekkFraSkatt: TrekkFraSkatt): List<PeriodeFraSkatt> {
    val perioderEn = lagPeriodeForTrekkFraSkatt(trekkFraSkatt, TrekkAlternativ.LOPM, idag, idag plus 3.mnd, 3000.0)
    val perioderTo = lagPeriodeForTrekkFraSkatt(trekkFraSkatt, TrekkAlternativ.LOPM, idag plus 3.mnd plus 1.dager, idag plus 5.mnd, 2000.0)
    val perioderTre = lagPeriodeForTrekkFraSkatt(trekkFraSkatt, TrekkAlternativ.LOPM, idag plus 5.mnd plus 1.dager, idag plus 7.mnd, 1000.0)

    return listOf(perioderEn, perioderTo, perioderTre).mapNewFomTom()
}

private fun List<PeriodeFraSkatt>.toKnownPeriods() =
    map {
        PeriodeTilOS(
            sats = it.trekkprosent ?: it.trekkbeloep ?: 0.0,
            periodeFomDato = it.startdato,
            periodeTomDato = it.sluttdato,
        )
    }

private fun lagBetalingsinformasjonForTrekkFraSkatt(trekkFraSkatt: TrekkFraSkatt): BetalingsinformasjonFraSkatt {
    val betalingsmottaker = "971648199"
    val kidnummer = "2191507714"
    val kontonummer = "70213997155"
    return BetalingsinformasjonFraSkatt(1L, trekkFraSkatt.id, betalingsmottaker, kidnummer, kontonummer)
}