package no.nav.sokos.utleggstrekk.service

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

import no.nav.sokos.utleggstrekk.database.RepositoryNy
import no.nav.sokos.utleggstrekk.database.model.SkattTrekkStatus
import no.nav.sokos.utleggstrekk.database.model.TransaksjonOS
import no.nav.sokos.utleggstrekk.database.model.TransaksjonsStatus
import no.nav.sokos.utleggstrekk.domene.nav.Aksjonskode
import no.nav.sokos.utleggstrekk.domene.nav.TrekkAlternativ
import no.nav.sokos.utleggstrekk.domene.ske.Trekkpaalegg
import no.nav.sokos.utleggstrekk.domene.ske.Trekkprosent
import no.nav.sokos.utleggstrekk.domene.ske.Trekkstatus
import no.nav.sokos.utleggstrekk.domene.ske.TrekkstorrelseForPeriode
import no.nav.sokos.utleggstrekk.listener.DBListener
import no.nav.sokos.utleggstrekk.util.TestData.makeTrekkpaalegg
import no.nav.sokos.utleggstrekk.util.dager
import no.nav.sokos.utleggstrekk.util.etter
import no.nav.sokos.utleggstrekk.util.idag
import no.nav.sokos.utleggstrekk.util.mnd

class BehandleTrekkServiceIntegrationTestNy :
    BehaviorSpec({
        extensions(DBListener)
        val repository = RepositoryNy(DBListener.dataSource)
        val behandleTrekkService = BehandleTrekkServiceNy(repository)

        fun storedInDb(trekk: Trekkpaalegg): Long? = repository.insertTrekkFraSkatt(trekk)

        fun storedInDbAndBehandlet(trekk: Trekkpaalegg) {
            val id = storedInDb(trekk)
            behandleTrekkService.behandleTrekk()
            repository.getTrekkFraSkattStatus(id!!) shouldBe SkattTrekkStatus.BEHANDLET
        }

        fun storedInDbBehandletAndSentOK(trekk: Trekkpaalegg) {
            storedInDbAndBehandlet(trekk)
            val transaksjoner = repository.getTransaksjonerTilOsSomIkkeErSendt()
            transaksjoner.size shouldBe 1
            repository.updateTransaksjonStatus(transaksjoner.first().transaksjonsID, TransaksjonsStatus.SENDT)
        }

        Given("Det finnes ett trekk i databasen med trekkstatus AKTIV, status MOTTATT som har én periode med prosenttrekk") {
            val trekkpaalegg =
                makeTrekkpaalegg(
                    trekkId = "trekkid1",
                    sekvensnummer = 1,
                    trekkversjon = 1,
                    trekkstatus = Trekkstatus.AKTIV,
                    perioder = listOf(TrekkstorrelseForPeriode(idag etter 2.dager, idag etter 2.mnd, trekkprosent = Trekkprosent(20.0))),
                )

            storedInDb(trekkpaalegg)

            When("Trekk skal behandles") {
                behandleTrekkService.behandleTrekk()
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

            val trekkstorrelseForPeriode1 = TrekkstorrelseForPeriode(idag etter 2.dager, idag etter 2.mnd, trekkprosent = Trekkprosent(20.0))
            val trekk1 =
                makeTrekkpaalegg(
                    trekkid,
                    sekvensnummer = 1,
                    trekkversjon = 1,
                    perioder = listOf(trekkstorrelseForPeriode1),
                )

            val trekkstorrelseForPeriode2 = TrekkstorrelseForPeriode(idag etter 1.mnd, idag etter 3.mnd, trekkprosent = Trekkprosent(20.0))
            val trekk2 =
                makeTrekkpaalegg(
                    trekkid,
                    sekvensnummer = 2,
                    trekkversjon = 2,
                    perioder = listOf(trekkstorrelseForPeriode2),
                )

            storedInDbBehandletAndSentOK(trekk1)
            storedInDb(trekk2)

            When("Trekk skal behandles") {
                behandleTrekkService.behandleTrekk()
                Then("Skal det produseres et trekk til OS med status ENDR av typen LOPP med nulling av den gamle perioden og opprettelse av ny periode") {
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
    })