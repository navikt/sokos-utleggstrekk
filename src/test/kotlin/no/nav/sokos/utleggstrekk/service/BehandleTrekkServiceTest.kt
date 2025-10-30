package no.nav.sokos.utleggstrekk.service

import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEmpty
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

        fun lagTestTrekkFraSkatt(trekkVersjon: Int = 1): TrekkFraSkatt {
            val tabellEntryId = 1L
            val trekkVersjon = trekkVersjon
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

        fun lagBetalingsinformasjonForTrekkFraSkatt(trekkFraSkatt: TrekkFraSkatt): BetalingsinformasjonFraSkatt {
            val betalingsmottaker = "971648198"
            val kidnummer = "17654202404"
            val kontonummer = "76940512057"
            return BetalingsinformasjonFraSkatt(1L, trekkFraSkatt.id, betalingsmottaker, kidnummer, kontonummer)
        }

        fun setUpBehandleTrekkServiceNy(
            alleTrekkSomIkkeErSendt: List<TrekkFraSkatt>,
            perioderForTrekkFraSkatt: List<PeriodeFraSkatt>,
            alleTrekkForTrekkId: List<TrekkFraSkatt>,
        ): BehandleTrekkServiceNy {
            val betalingsinformasjonForTrekkFraSkatt: BetalingsinformasjonFraSkatt = lagBetalingsinformasjonForTrekkFraSkatt(alleTrekkSomIkkeErSendt.first())
            val behandleTrekkServiceNy = spyk(BehandleTrekkServiceNy(mockk(relaxed = true)))
            every { behandleTrekkServiceNy.trekkSomSkalSendes() } returns alleTrekkSomIkkeErSendt
            every { behandleTrekkServiceNy.perioderForTrekkVersjon(any()) } returns perioderForTrekkFraSkatt
            every { behandleTrekkServiceNy.trekkForTrekkId(any()) } returns alleTrekkForTrekkId
            every { behandleTrekkServiceNy.betalingsInformasjonForTrekk(any()) } returns betalingsinformasjonForTrekkFraSkatt

            return behandleTrekkServiceNy
        }
        Given("Trekkdokument dannes") {

            val trekkVersjon = 1
            val trekkFraSkatt = lagTestTrekkFraSkatt(trekkVersjon)
            val alleTrekkSomIkkeErSendt = listOf(trekkFraSkatt)
            val betalingsinformasjonForTrekkFraSkatt: BetalingsinformasjonFraSkatt = lagBetalingsinformasjonForTrekkFraSkatt(trekkFraSkatt)

            val perioderForTrekkVersjon = listOf(lagPeriodeForTrekkFraSkatt(trekkFraSkatt, TrekkAlternativ.LOPM, sats = 5000.0))
            val behandleTrekkServiceNy = setUpBehandleTrekkServiceNy(alleTrekkSomIkkeErSendt, perioderForTrekkVersjon, emptyList())

            val periodeInformasjon = behandleTrekkServiceNy.utledTrekkAlternativForPeriode(perioderForTrekkVersjon)

            val periode = periodeInformasjon.first()
            When("Trekkdokument dannes for trekk fra skatt") {
                val trekkDokument = behandleTrekkServiceNy.lagTrekkDokument(trekkFraSkatt, periodeInformasjon)

                val innrapporteringTrekk = trekkDokument.innrapporteringTrekk

                Then("Skal all informasjon være med") {
                    innrapporteringTrekk.aksjonskode shouldBe Aksjonskode.NY
                    innrapporteringTrekk.kreditorTrekkId shouldBe "${trekkFraSkatt.trekkid}${innrapporteringTrekk.kodeTrekkAlternativ.value}"
                    innrapporteringTrekk.kodeTrekkAlternativ shouldBe periode.trekkalternativ

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
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDate()
                            .toString()
                    innrapporteringTrekk.perioder.periode
                        .first()
                        .periodeFomDato shouldBe periode.periodeFraSkatt.startdato
                    innrapporteringTrekk.perioder.periode
                        .first()
                        .periodeTomDato shouldBe periode.periodeFraSkatt.sluttdato
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
            And("Perioden har kun 1 trekkalternativ") {
                When("Trekket har trekkversjon 1") {
                    val trekkVersjon = 1
                    val trekkFraSkatt = lagTestTrekkFraSkatt(trekkVersjon)
                    val alleTrekkSomIkkeErSendt = listOf(trekkFraSkatt)

                    val perioderForTrekkVersjon = lagPerioderForSkattLOPM(trekkFraSkatt)
                    val allePerioderForTrekkId = perioderForTrekkVersjon
                    val behandleTrekkServiceNy = setUpBehandleTrekkServiceNy(alleTrekkSomIkkeErSendt, perioderForTrekkVersjon, emptyList())

                    val periodeInformasjon = behandleTrekkServiceNy.utledTrekkAlternativForPeriode(perioderForTrekkVersjon)
                    val trekkDokument = behandleTrekkServiceNy.lagTrekkDokument(behandleTrekkServiceNy.trekkSomSkalSendes().first(), periodeInformasjon).innrapporteringTrekk
                    Then("Skal trekket bli til 1 NYTT trekk med 3 perioder") {
                        trekkDokument.aksjonskode shouldBe Aksjonskode.NY
                        trekkDokument.perioder.periode.size shouldBe 3
                    }
                }
                When("Trekket har trekkversjon 2") {
                    val trekkVersjon = 2
                    val trekkFraSkatt = lagTestTrekkFraSkatt(trekkVersjon)
                    val alleTrekkSomIkkeErSendt = listOf(trekkFraSkatt)
                    val allePerioderForTrekkVersjon = lagPerioderForSkattLOPM(trekkFraSkatt)

                    val eksisterendeTrekk = listOf(lagTestTrekkFraSkatt(1))

                    And("Vi har fått trekkversjon 1") {
                        val alleTrekkForTrekkId = eksisterendeTrekk
                        val behandleTrekkServiceNy = setUpBehandleTrekkServiceNy(alleTrekkSomIkkeErSendt, allePerioderForTrekkVersjon, alleTrekkForTrekkId)
                        val periodeInformasjon = behandleTrekkServiceNy.utledTrekkAlternativForPeriode(allePerioderForTrekkVersjon)
                        val trekkDokument = behandleTrekkServiceNy.lagTrekkDokument(behandleTrekkServiceNy.trekkSomSkalSendes().first(), periodeInformasjon).innrapporteringTrekk

                        Then("Skal trekket bli til 1 ENDRET trekk med 3 perioder") {
                            trekkDokument.aksjonskode shouldBe Aksjonskode.ENDR
                            trekkDokument.perioder.periode.size shouldBe 3
                        }
                    }
                    And("Vi ikke har fått trekkversjon 1") {
                        val behandleTrekkServiceNy = setUpBehandleTrekkServiceNy(alleTrekkSomIkkeErSendt, allePerioderForTrekkVersjon, emptyList())
                        val periodeInformasjon = behandleTrekkServiceNy.utledTrekkAlternativForPeriode(allePerioderForTrekkVersjon)
                        val trekkDokument = behandleTrekkServiceNy.lagTrekkDokument(behandleTrekkServiceNy.trekkSomSkalSendes().first(), periodeInformasjon).innrapporteringTrekk
                        Then("Skal trekket bli til 1 NYTT trekk med 3 perioder") {

                            trekkDokument.aksjonskode shouldBe Aksjonskode.NY
                            trekkDokument.perioder.periode.size shouldBe 3
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
