package no.nav.sokos.utleggstrekk.service.gammel

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

import no.nav.sokos.utleggstrekk.database.Repository
import no.nav.sokos.utleggstrekk.database.TestRepositoryExtensions.clearDb
import no.nav.sokos.utleggstrekk.database.model.UtleggstrekkStatus
import no.nav.sokos.utleggstrekk.database.model.UtleggstrekkTable
import no.nav.sokos.utleggstrekk.domene.nav.Aksjonskode
import no.nav.sokos.utleggstrekk.domene.nav.TrekkAlternativ
import no.nav.sokos.utleggstrekk.domene.nav.TrekkTilOppdrag
import no.nav.sokos.utleggstrekk.domene.ske.Trekkpaalegg
import no.nav.sokos.utleggstrekk.domene.ske.Trekkprosent
import no.nav.sokos.utleggstrekk.domene.ske.TrekkstorrelseForPeriode
import no.nav.sokos.utleggstrekk.service.BehandleTrekkService
import no.nav.sokos.utleggstrekk.service.DatabaseService
import no.nav.sokos.utleggstrekk.service.withTransaction
import no.nav.sokos.utleggstrekk.util.TestContainer
import no.nav.sokos.utleggstrekk.util.TestData

class BehandleTrekkServiceIntegrationTest :
    BehaviorSpec({

        xcontext("disabled") {
            val testContainer = TestContainer()
            val dataSource = testContainer.dataSource
            val repository = Repository(dataSource)
            val dbService = DatabaseService(dataSource)
            val behandleTrekkService = BehandleTrekkService(dbService)

            beforeSpec { dataSource.withTransaction { session -> repository.clearDb(session) } }

            fun storedInDb(trekk: Trekkpaalegg) {
                dataSource.withTransaction { session -> repository.saveAllNewUtleggstrekk(listOf(trekk), session) }
            }

            fun storedInDbAndSent(trekk: Trekkpaalegg): UtleggstrekkTable {
                storedInDb(trekk)
                return dataSource.withTransaction { session ->
                    val utleggstrekk = repository.fetchTrekkNotSendt(session).find { it.sekvensnummer == trekk.sekvensnummer }
                    repository.updateNavTrekkStatus(utleggstrekk!!.corrid, UtleggstrekkStatus.SENDT, session)
                    utleggstrekk
                }
            }

            fun storedInDbAndSentOk(trekk: Trekkpaalegg, trekkAlternativ: TrekkAlternativ): UtleggstrekkTable {
                val utleggstrekk = storedInDbAndSent(trekk)
                dataSource.withTransaction { session ->
                    repository.updateKvitteringStatus(
                        utleggstrekk.corrid,
                        UtleggstrekkStatus.KVITTERING_OK,
                        navTrekkId = "00123456",
                        kvittering = "00",
                        trekkalternativ = trekkAlternativ,
                        session = session,
                    )
                }
                return utleggstrekk
            }

            Given("Det finnes ett trekk i databasen med trekkstatus AKTIV, status MOTTATT som har én periode med prosenttrekk") {
                beforeContainer {
                    storedInDb(
                        TestData.Trekkpaalegg(
                            "trekkid1",
                            1,
                            1,
                            perioder = listOf(TrekkstorrelseForPeriode("2026-02-02", "2026-04-02", trekkprosent = Trekkprosent(20.0))),
                        ),
                    )
                }
                afterContainer { dataSource.withTransaction { session -> repository.clearDb(session) } }

                When("Trekk skal behandles") {
                    Then("Skal det produseres ett nytt trekk til OS med status NY til OS med trekkalternativ LOPP") {
                        val behandlet = behandleTrekkService.lagTrekkSomSkalSendes()

                        behandlet.keys.size shouldBe 1
                        behandlet.values.size shouldBe 1
                        val melding: TrekkTilOppdrag = behandlet.values.first().first()
                        melding.dokument.innrapporteringTrekk.aksjonskode shouldBe Aksjonskode.NY
                        melding.dokument.innrapporteringTrekk.kodeTrekkAlternativ shouldBe TrekkAlternativ.LOPP
                    }
                }
            }
        }

        /* TODO:  Feilende test
        Given("Det finnes to trekk i databasen med trekkstatus AKTIV, et med status SENDT og ett med MOTTATT som er versjon 2 med endrede periodetider men begge prosenttrekk") {
            // SENDT betyr at OS ennå ikke har kvittert for versjon 1 av trekket, men vi antar optimistisk her at at vil gå bra.
            beforeContainer {
                val trekk1 =
                    TestData.Trekkpaalegg(
                        "trekkid1",
                        1,
                        1,
                        perioder = listOf(TrekkstorrelseForPeriode("2026-02-02", "2026-04-02", trekkprosent = Trekkprosent(20.0))),
                    )

                val trekk2 =
                    TestData.Trekkpaalegg(
                        "trekkid1",
                        2,
                        2,
                        perioder = listOf(TrekkstorrelseForPeriode("2026-02-05", "2026-04-08", trekkprosent = Trekkprosent(20.0))),
                    )

                dataSource.withTransaction { session ->
                    repository.saveAllNewUtleggstrekk(listOf(trekk1), session)
                }
                dataSource.withTransaction { session ->
                    val trekk = repository.fetchTrekkNotSendt(session).first()
                    repository.updateNavTrekkStatus(trekk.corrid, SENDT, session)
                }

                dataSource.withTransaction { session ->
                    repository.saveAllNewUtleggstrekk(listOf(trekk2), session)
                }
            }
            afterContainer { dataSource.withTransaction { session -> repository.clearDb(session) } }

            When("Trekk skal behandles") {
                Then("Skal det produseres et trekk til OS med status ENDR av typen LOPEP med nulling av den gamle perioden og opprettelse av ny periode") {
                    val behandlet = behandleTrekkService.lagTrekkSomSkalSendes()
                    behandlet.keys.size shouldBe 1
                    behandlet.values.size shouldBe 1
                    val melding: TrekkTilOppdrag = behandlet.values.first().first()
                    melding.dokument.innrapporteringTrekk.aksjonskode shouldBe Aksjonskode.ENDR
                    melding.dokument.innrapporteringTrekk.kodeTrekkAlternativ shouldBe TrekkAlternativ.LOPP

                    val perioder = melding.dokument.innrapporteringTrekk.perioder.periode

                    println("perioder " + perioder)

                    perioder.size shouldBe 2
                }
            }
        }


        // TODO: Feilende test
        Given("Det finnes to trekk i databasen med trekkstatus AKTIV, et med status KVITTERING_OK og ett med MOTTATT som er versjon 2 med endrede periodetider men fremdeles prosenttrekk") {
            beforeContainer {
                val trekk1 =
                    storedInDbAndSentOk(
                        TestData.Trekkpaalegg(
                            "trekkid1",
                            1,
                            1,
                            perioder = listOf(TrekkstorrelseForPeriode("2027-02-02", "2027-04-02", trekkprosent = Trekkprosent(20.0))),
                        ),
                        TrekkAlternativ.LOPP,
                    )

                val trekk2 =
                    storedInDb(
                        TestData.Trekkpaalegg(
                            "trekkid1",
                            2,
                            2,
                            perioder = listOf(TrekkstorrelseForPeriode("2027-02-05", "2027-04-08", trekkprosent = Trekkprosent(20.0))),
                        ),
                    )
            }
            afterContainer { dataSource.withTransaction { session -> repository.clearDb(session) } }

            When("Trekk skal behandles") {
                Then("Skal det produseres et trekk til OS med status ENDR av typen LOPEP med nulling av den gamle perioden og opprettelse av ny periode") {

                    val behandlet = behandleTrekkService.lagTrekkSomSkalSendes()
                    behandlet.keys.size shouldBe 1
                    behandlet.values.size shouldBe 1
                    val melding: TrekkTilOppdrag = behandlet.values.first().first()
                    melding.dokument.innrapporteringTrekk.aksjonskode shouldBe Aksjonskode.ENDR
                    melding.dokument.innrapporteringTrekk.kodeTrekkAlternativ shouldBe TrekkAlternativ.LOPP

                    val perioder = melding.dokument.innrapporteringTrekk.perioder.periode
                    perioder.size shouldBe 2
                }
            }
        }


        // Hoppe over feilet trekk fordi det har kommet ny versjon
        Given("Det finnes to trekk i databasen, et med status KVITTERING_FEILET og ett med MOTTATT som er versjon 2 med endrede periodetider men fremdeles prosenttrekk") {
            When("Trekk skal behandles") {
                Then("Skal det produseres et trekk til OS med status NY av typen LOPEP med opprettelse av ny periode, KVITTERING_FEILET skal få status HOPPET_OVER") {
                }
            }
        }

         */
    })