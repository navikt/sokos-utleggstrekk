package no.nav.sokos.utleggstrekk.service

import kotlin.time.Duration.Companion.seconds

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

import no.nav.sokos.utleggstrekk.database.RepositoryNy
import no.nav.sokos.utleggstrekk.database.model.SkattTrekkStatus
import no.nav.sokos.utleggstrekk.database.model.TransaksjonOS
import no.nav.sokos.utleggstrekk.database.model.TransaksjonsStatus
import no.nav.sokos.utleggstrekk.domene.nav.Aksjonskode
import no.nav.sokos.utleggstrekk.domene.nav.InnrapporteringTrekk
import no.nav.sokos.utleggstrekk.domene.nav.OSDto
import no.nav.sokos.utleggstrekk.domene.nav.Periode
import no.nav.sokos.utleggstrekk.domene.nav.Perioder
import no.nav.sokos.utleggstrekk.domene.nav.TrekkAlternativ
import no.nav.sokos.utleggstrekk.domene.ske.Trekkprosent
import no.nav.sokos.utleggstrekk.domene.ske.Trekkstatus
import no.nav.sokos.utleggstrekk.domene.ske.TrekkstorrelseForPeriode
import no.nav.sokos.utleggstrekk.listener.DBListener
import no.nav.sokos.utleggstrekk.util.TestData.makeTrekkpaalegg

class BehandleTrekkServiceIntegrationTestNy :
    BehaviorSpec({

        extensions(DBListener)
        val repository = RepositoryNy(DBListener.dataSource)
        val behableTrekkService = BehandleTrekkServiceNy(repository)

        Given("Det finnes ett trekk i databasen med trekkstatus AKTIV, status MOTTATT som har én periode med prosenttrekk") {
            val trekkpaalegg =
                makeTrekkpaalegg(
                    trekkId = "trekkid1",
                    sekvensnummer = 1,
                    trekkversjon = 1,
                    trekkstatus = Trekkstatus.AKTIV,
                    perioder = listOf(TrekkstorrelseForPeriode("2026-02-02", "2026-04-02", trekkprosent = Trekkprosent(20.0))),
                )

            repository.insertTrekkFraSkatt(trekkpaalegg)

            When("Trekk skal behandles") {
                behableTrekkService.behandleTrekk()
                Then("Skal det produseres ett nytt trekk til OS med aksjonskode NY og trekkalternativ LOPP") {
                    val transaksjonerTilOS = repository.getTransaksjonerTilOsSomIkkeErSendt()
                    transaksjonerTilOS.shouldHaveSize(1)
                    transaksjonerTilOS.first().trekkAlternativ shouldBe TrekkAlternativ.LOPP
                    transaksjonerTilOS.first().aksjonskode shouldBe Aksjonskode.NY
                }
            }
        }

        Given("Det finnes to trekk i databasen med trekkstatus AKTIV, et med status SENDT og ett med IKKE_SENDT som er versjon 2 med endrede periodetider men begge prosenttrekk") {
            DBListener.clearDB()
            val trekkid = "trekkid1"

            val trekkstorrelseForPeriode1 = TrekkstorrelseForPeriode("2026-02-02", "2026-04-02", trekkprosent = Trekkprosent(20.0))
            val trekk1 =
                makeTrekkpaalegg(
                    trekkid,
                    sekvensnummer = 1,
                    trekkversjon = 1,
                    perioder = listOf(trekkstorrelseForPeriode1),
                )

            val trekkstorrelseForPeriode2 = TrekkstorrelseForPeriode("2026-02-05", "2026-04-08", trekkprosent = Trekkprosent(20.0))
            val trekk2 =
                makeTrekkpaalegg(
                    trekkid,
                    sekvensnummer = 2,
                    trekkversjon = 2,
                    perioder = listOf(trekkstorrelseForPeriode2),
                )

            val fraskattId1 = repository.insertTrekkFraSkatt(trekk1)!!
            repository.insertTrekkFraSkatt(trekk2)

            val transaksjonID1 = "TransaksjonId1"
            val innrapporteringTrekk1 =
                InnrapporteringTrekk(
                    aksjonskode = Aksjonskode.NY,
                    kreditorIdTss = "KreditorIdTSS",
                    kreditorTrekkId = trekkid,
                    kreditorsRef = "KreditorsRef",
                    kodeTrekkAlternativ = TrekkAlternativ.LOPP,
                    debitorId = "debitorId",
                    kid = "kid",
                    prioritetFomDato = "2026-02-02",
                    perioder =
                        Perioder(
                            listOf(Periode(trekkstorrelseForPeriode1.startdato, trekkstorrelseForPeriode1.sluttdato ?: "", trekkstorrelseForPeriode1.trekkprosent?.trekkprosent ?: 0.0)),
                        ),
                )
            val dto = OSDto(transaksjonID1, trekkid, innrapporteringTrekk1, "dokumentJson")
            repository.insertTransaksjonTilOs(dto)
            repository.updateTransaksjonStatus(transaksjonID1, TransaksjonsStatus.SENDT)
            repository.updateTrekkFraSkattStatus(fraskattId1, SkattTrekkStatus.BEHANDLET)

            When("Trekk skal behandles") {
                behableTrekkService.behandleTrekk()
                Then("Skal det produseres et trekk til OS med status ENDR av typen LOPP med nulling av den gamle perioden og opprettelse av ny periode") {

                    eventually(1.seconds) {

                        val transaksjonerTilOS: List<TransaksjonOS> = repository.getTransaksjonerTilOsForTrekkID(trekkid)
                        transaksjonerTilOS.shouldHaveSize(2)
                        val sendt =
                            transaksjonerTilOS.filter { it.transaksjonStatus == TransaksjonsStatus.SENDT }.let {
                                it.shouldHaveSize(1)
                                it.first()
                            }

                        sendt.trekkAlternativ shouldBe TrekkAlternativ.LOPP
                        sendt.aksjonskode shouldBe Aksjonskode.NY

                        val ikkeSendt =
                            transaksjonerTilOS.filter { it.transaksjonStatus == TransaksjonsStatus.IKKE_SENDT }.let {
                                it.shouldHaveSize(1)
                                it.first()
                            }

                        ikkeSendt.trekkAlternativ shouldBe TrekkAlternativ.LOPP
                        ikkeSendt.aksjonskode shouldBe Aksjonskode.ENDR
                        ikkeSendt.perioder.shouldHaveSize(2)

                        val nullPeriode =
                            ikkeSendt.perioder.filter { it.sats == 0.0 }.let {
                                it.shouldHaveSize(1)
                                it.first()
                            }

                        nullPeriode.periodeFomDato shouldBe trekkstorrelseForPeriode1.startdato
                        nullPeriode.periodeTomDato shouldBe trekkstorrelseForPeriode1.sluttdato!!

                        val gjeldendePeriode =
                            ikkeSendt.perioder.filter { it.sats != 0.0 }.let {
                                it.shouldHaveSize(1)
                                it.first()
                            }

                        gjeldendePeriode.periodeFomDato shouldBe trekkstorrelseForPeriode2.startdato
                        gjeldendePeriode.periodeTomDato shouldBe trekkstorrelseForPeriode2.sluttdato!!
                        gjeldendePeriode.sats shouldBe trekkstorrelseForPeriode2.trekkprosent?.trekkprosent!!
                    }
                }
            }
        }
    })