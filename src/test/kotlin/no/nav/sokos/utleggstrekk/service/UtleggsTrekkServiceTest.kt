package no.nav.sokos.utleggstrekk.service

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk

import no.nav.sokos.utleggstrekk.client.SkeClient
import no.nav.sokos.utleggstrekk.database.model.TransaksjonsStatus
import no.nav.sokos.utleggstrekk.domene.ske.Betalingsinformasjon
import no.nav.sokos.utleggstrekk.domene.ske.Trekkpaalegg
import no.nav.sokos.utleggstrekk.domene.ske.Trekkprosent
import no.nav.sokos.utleggstrekk.domene.ske.Trekkstatus
import no.nav.sokos.utleggstrekk.domene.ske.TrekkstorrelseForPeriode
import no.nav.sokos.utleggstrekk.listener.DBListener
import no.nav.sokos.utleggstrekk.listener.DBListener.RepositoryNy
import no.nav.sokos.utleggstrekk.mq.JmsProducerService
import no.nav.sokos.utleggstrekk.util.TestData.makeTrekkpaalegg
import no.nav.sokos.utleggstrekk.util.dager
import no.nav.sokos.utleggstrekk.util.etter
import no.nav.sokos.utleggstrekk.util.idag

internal class UtleggsTrekkServiceTest :
    BehaviorSpec({

        extensions(DBListener)

        val capturedPayloads = mutableListOf<String>()

        val mqProducerMock =
            mockk<JmsProducerService>(relaxed = true) {
                every { send(capture(capturedPayloads)) } just Runs
            }

        Given("Vi henter ett trekk fra SKE") {
            val mottaker = Betalingsinformasjon("tssIDSkatt", "13812738912427", "6123101233424")
            val trekkPeriode =
                TrekkstorrelseForPeriode(
                    "2026-02-02",
                    "2026-04-02",
                    trekkprosent = Trekkprosent(20.0),
                )

            val trekkpaalegg =
                makeTrekkpaalegg(
                    trekkId = "ID1",
                    sekvensnummer = 1,
                    trekkversjon = 1,
                    saksnummer = "SAK1",
                    trekkpliktig = "123456789",
                    skyldner = "skyldner",
                    trekkstatus = Trekkstatus.AKTIV,
                    mottaker = mottaker,
                    perioder = listOf(trekkPeriode),
                )
            val skeClientMock =
                mockk<SkeClient> {
                    coEvery { hentUtleggstrekkFraSekvensnr(any()) } returns listOf(trekkpaalegg)
                }

            val utleggsTrekkService =
                UtleggsTrekkService(
                    RepositoryNy,
                    skeClient = skeClientMock,
                    mqProducer = mqProducerMock,
                )

            When("Trekk sendes") {
                utleggsTrekkService.schedule()

                // TODO: Skal håndtering av serialisering flyttes til JMSProducer?
                Then("Skal trekk serialiseres riktig") {
                    capturedPayloads.forEach {
                        it shouldContain "SOKOSUTLEGG"
                        it shouldContain "TRK1"
                    }
                }
                Then("Skal trekkpaalegget lagres i databasen") {
                    val trekkFraSkatt = RepositoryNy.getTrekkFraSkatt(trekkpaalegg.trekkid, trekkpaalegg.trekkversjon)
                    trekkFraSkatt.shouldNotBeNull()

                    withClue("Perioder skal lagres") {
                        val perioder = RepositoryNy.getPerioderForTrekkVersjon(trekkFraSkatt.id)
                        perioder.size shouldBe 1
                        val periode = perioder.first()
                        periode.trekkprosent.shouldNotBeNull()
                        periode.trekkprosent shouldBe trekkPeriode.trekkprosent!!.trekkprosent
                        periode.trekkbeloep.shouldBeNull()
                        periode.trekkbeloep shouldBe trekkPeriode.trekkbeloep
                    }

                    withClue("Betalingsinformasjon skal lagres") {
                        val betalingsInformasjon = RepositoryNy.getBetalingsinformasjonForTrekk(trekkFraSkatt.id)

                        betalingsInformasjon.shouldNotBeNull()
                        betalingsInformasjon.betalingsmottaker shouldBe mottaker.betalingsmottaker
                    }
                }

                Then("Skal transaksjon oppdateres") {
                    val transaksjoner = RepositoryNy.getTransaksjonerTilOsForTrekkID(trekkpaalegg.trekkid)
                    transaksjoner.forEach { it.transaksjonStatus shouldBe TransaksjonsStatus.SENDT }
                }
            }
        }
        Given("Vi henter nye trekk fra SKE og det er 10 nye og maksantall er 4") {
            DBListener.clearDB()
            val mottaker = Betalingsinformasjon("tssIDSkatt", "13812738912427", "6123101233424")
            val trekkpaalegg: List<Trekkpaalegg> =
                (1..10).map { index ->
                    makeTrekkpaalegg(
                        trekkId = "ID1",
                        sekvensnummer = index,
                        trekkversjon = index,
                        saksnummer = "SAK1",
                        trekkpliktig = "123456789",
                        skyldner = "skyldner",
                        trekkstatus = Trekkstatus.AKTIV,
                        mottaker = mottaker,
                        perioder =
                            listOf(
                                TrekkstorrelseForPeriode(
                                    idag etter (index * 8).dager,
                                    idag etter (index * 8 + 5).dager,
                                    trekkprosent = Trekkprosent(20.0),
                                ),
                            ),
                    )
                }
            val parts = trekkpaalegg.chunked(4)

            val skeClientMock =
                mockk<SkeClient> {
                    parts.forEach { part ->
                        coEvery { hentUtleggstrekkFraSekvensnr(eq(part.first().sekvensnummer - 1)) } returns part
                    }
                }

            val utleggsTrekkService =
                UtleggsTrekkService(
                    RepositoryNy,
                    maxAntall = 4,
                    skeClient = skeClientMock,
                    mqProducer = mqProducerMock,
                )

            When("Trekk hentes") {
                utleggsTrekkService.schedule()
                Then("Gjøres det ${parts.size} kall mot SkeClient helt til alle er nye utleggstrekk er hentet") {
                    coVerify(exactly = parts.size) { skeClientMock.hentUtleggstrekkFraSekvensnr(any()) }
                }
            }
        }
    })