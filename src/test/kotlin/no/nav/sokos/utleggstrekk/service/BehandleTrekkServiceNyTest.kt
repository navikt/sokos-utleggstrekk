import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.just
import io.mockk.mockk

import no.nav.sokos.utleggstrekk.database.model.TrekkPeriodeTable
import no.nav.sokos.utleggstrekk.database.model.UtleggstrekkStatus.MOTTATT
import no.nav.sokos.utleggstrekk.database.model.UtleggstrekkTable
import no.nav.sokos.utleggstrekk.domene.nav.Aksjonskode
import no.nav.sokos.utleggstrekk.domene.nav.TrekkAlternativ
import no.nav.sokos.utleggstrekk.domene.nav.TrekkTilOppdrag
import no.nav.sokos.utleggstrekk.domene.ske.Trekkstatus.AKTIVE
import no.nav.sokos.utleggstrekk.service.BehandleTrekkService
import no.nav.sokos.utleggstrekk.service.DatabaseService
import no.nav.sokos.utleggstrekk.util.TestData
import no.nav.sokos.utleggstrekk.util.TestData.trekkPeriode

// TODO: Spør Endre om det er viktig å slette trekk dersom ett trekk ikke lenger har to trekktyper.

class BehandleTrekkServiceNyTest :
    BehaviorSpec({
        val databaseServiceMock = mockk<DatabaseService>()
        val behandleTrekkService = BehandleTrekkService(databaseServiceMock)

        Given("Det finnes ett trekk i databasen med trekkstatus AKTIVE, status MOTTATT som har én periode prosenttrekk") {
            val trekk = TestData.UtleggstrekkTable(1, "ske1", 1, AKTIVE, MOTTATT)
            val periode = trekk.trekkPeriode(100.0, TrekkAlternativ.LOPP, "2026-02-02", "2026-04-02")

            coEvery { databaseServiceMock.hentAlleTrekkSomIkkeErSendt() } returns listOf(trekk)
            coEvery { databaseServiceMock.hentAllePerioderForTrekkId(any() as UtleggstrekkTable) } returns listOf(periode)
            coEvery { databaseServiceMock.hentAllePerioderForTrekkVersjon(any() as UtleggstrekkTable) } returns listOf(periode)
            coEvery { databaseServiceMock.lagreGenerertePerioder(any() as List<TrekkPeriodeTable>) } just Runs

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

        /*

        Given("Det finnes to trekk i databasen med trekkstatus AKTIV, et med status SENDT og ett med MOTTATT som er versjon 2 med endrede periodetider men begge prosenttrekk") {
            // SENDT betyr at OS ennå ikke har kvittert for versjon 1 av trekket, men vi antar optimistisk her at at vil gå bra.

            When("Trekk skal behandles") {
                Then("Skal det produseres et trekk til OS med status ENDR av typen LOPEP med nulling av den gamle perioden og opprettelse av ny periode") {
                }
            }
        }

        Given("Det finnes to trekk i databasen med trekkstatus AKTIV, et med status KVITTERING_OK og ett med MOTTATT som er versjon 2 med endrede periodetider men fremdeles prosenttrekk") {
            When("Trekk skal behandles") {
                Then("Skal det produseres et trekk til OS med status ENDR av typen LOPEP med nulling av den gamle perioden og opprettelse av ny periode") {
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