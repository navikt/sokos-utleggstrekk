package no.nav.sokos.utleggstrekk.service

import java.time.LocalDateTime

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import mu.KLogger
import mu.KotlinLogging
import mu.Marker

import no.nav.sokos.utleggstrekk.client.SkeClient
import no.nav.sokos.utleggstrekk.config.jsonConfig
import no.nav.sokos.utleggstrekk.database.RepositoryNy
import no.nav.sokos.utleggstrekk.database.model.SkattTrekkStatus
import no.nav.sokos.utleggstrekk.database.model.TransaksjonOS
import no.nav.sokos.utleggstrekk.database.model.TransaksjonsStatus
import no.nav.sokos.utleggstrekk.domene.nav.Aksjonskode
import no.nav.sokos.utleggstrekk.domene.nav.Document
import no.nav.sokos.utleggstrekk.domene.nav.InnrapporteringTrekk
import no.nav.sokos.utleggstrekk.domene.nav.TrekkAlternativ
import no.nav.sokos.utleggstrekk.domene.nav.TrekkTilOppdrag
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
import no.nav.sokos.utleggstrekk.util.idag
import no.nav.sokos.utleggstrekk.util.plus
import no.nav.sokos.utleggstrekk.util.resourceToString

internal class UtleggsTrekkServiceTest :
    BehaviorSpec({

        extensions(DBListener)

        val capturedPayloads = mutableListOf<String>()

        val mqProducerMock =
            mockk<JmsProducerService>(relaxed = true) {
                every { send(capture(capturedPayloads)) } just Runs
            }

        val logger =
            mockk<KLogger> {
                every { error(any<String>()) } returns Unit
                every { error(any<Marker>(), any<String>(), any<Exception>()) } returns Unit
                every { info(any<String>()) } returns Unit
                every { info(any<() -> Any?>()) } returns Unit
            }

        beforeSpec {
            mockkObject(KotlinLogging)
            every { KotlinLogging.logger(any<() -> Unit>()) } returns logger
        }

        Given("Vi henter ett trekk fra SKE") {
            val mottaker = Betalingsinformasjon(betalingsmottaker = "971648199", kidnummer = "13812738912427", kontonummer = "70213997155")
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
                    skyldner = "12345678901",
                    trekkstatus = Trekkstatus.AKTIV,
                    mottaker = mottaker,
                    perioder = listOf(trekkPeriode),
                )
            val skeClientMock =
                mockk<SkeClient> {
                    coEvery { hentUtleggstrekkFraSekvensnr(any()) } returns listOf(trekkpaalegg)
                }
            val slackService = mockk<SlackService>(relaxUnitFun = true)

            val utleggsTrekkService =
                UtleggsTrekkService(
                    RepositoryNy,
                    skeClient = skeClientMock,
                    slackService = slackService,
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
            val mottaker = Betalingsinformasjon(betalingsmottaker = "971648199", kidnummer = "13812738912427", kontonummer = "70213997155")
            val trekkpaalegg: List<Trekkpaalegg> =
                (1..10).map { index ->
                    makeTrekkpaalegg(
                        trekkId = "ID1",
                        sekvensnummer = index,
                        trekkversjon = index,
                        saksnummer = "SAK1",
                        trekkpliktig = "123456789",
                        skyldner = "12345678901",
                        trekkstatus = Trekkstatus.AKTIV,
                        mottaker = mottaker,
                        perioder =
                            listOf(
                                TrekkstorrelseForPeriode(
                                    idag plus (index * 8).dager,
                                    idag plus (index * 8 + 5).dager,
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
            val slackService = mockk<SlackService>(relaxUnitFun = true)

            val utleggsTrekkService =
                UtleggsTrekkService(
                    RepositoryNy,
                    skeClient = skeClientMock,
                    slackService = slackService,
                    maxAntall = 4,
                    mqProducer = mqProducerMock,
                )

            When("Trekk hentes") {
                utleggsTrekkService.schedule()
                Then("Gjøres det ${parts.size} kall mot SkeClient helt til alle er nye utleggstrekk er hentet") {
                    coVerify(exactly = parts.size) { skeClientMock.hentUtleggstrekkFraSekvensnr(any()) }
                }
            }
        }

        Given("Vi henter trekk fra SKE") {
            Then("Vi sender alarmer til slack hvis det stod en feil") {
                val slackService =
                    mockk<SlackService> {
                        coEvery { sendCachedErrors(any()) } returns Unit
                    }
                val utleggsTrekkService =
                    UtleggsTrekkService(
                        repositoryNy = mockk(relaxed = true),
                        skeClient = mockk(relaxed = true),
                        slackService = slackService,
                        mqProducer = mqProducerMock,
                    )

                utleggsTrekkService.schedule()
                coVerify(exactly = 1) { slackService.sendCachedErrors("Trekk henting feil") }
            }
        }

        Given("Vi henter transaksjoner til OS som mangler kvittering") {
            val now = LocalDateTime.now()
            val oldDate = now.minusDays(2)

            val mockTransaksjonOS =
                mockk<TransaksjonOS> {
                    every { transaksjonsID } returnsMany listOf("1", "2")
                    every { tidspunktSendt } returnsMany listOf(now, oldDate)
                }

            val repositoryNy =
                mockk<RepositoryNy> {
                    every { getTransakjonerTilOsSomManglerKvittering() } returns List(2) { mockTransaksjonOS }
                }

            val slackService =
                mockk<SlackService> {
                    every { addError(any(), any()) } returns Unit
                    coEvery { sendCachedErrors(any()) } returns Unit
                }

            val utleggsTrekkService =
                UtleggsTrekkService(
                    repositoryNy,
                    skeClient = mockk(),
                    slackService = slackService,
                    mqProducer = mqProducerMock,
                )

            When("Transaksjonen ble sendt for mer enn en døgn siden") {
                utleggsTrekkService.reportMissingKvittering()
                Then("Vi sender alarmer på slack") {
                    verify(exactly = 1) { slackService.addError(any(), any()) }
                    coVerify(exactly = 1) { slackService.sendCachedErrors(any()) }
                }
            }
        }
        Given("Vi får trekk som ikke validerer") {
            DBListener.clearDB()
            val trekkpaalegg = jsonConfig.decodeFromString<List<Trekkpaalegg>>(resourceToString("trekkMedFeil/trekkMedUgyldigPnr.json")).first()
            val skeClientMock =
                mockk<SkeClient> {
                    coEvery { hentUtleggstrekkFraSekvensnr(any()) } returns listOf(trekkpaalegg)
                }
            val slackService = mockk<SlackService>(relaxUnitFun = true)

            val utleggsTrekkService =
                UtleggsTrekkService(
                    RepositoryNy,
                    skeClient = skeClientMock,
                    slackService = slackService,
                    maxAntall = 2,
                    mqProducer = mqProducerMock,
                )

            When("Trekket prosesseres") {
                utleggsTrekkService.schedule()
                Then("Blir status AVVIST") {
                    RepositoryNy.getTrekkFraSkattStatus(1) shouldBe SkattTrekkStatus.AVVIST
                }
            }
        }

        Given("Vi sender trekk til OS") {
            val document =
                TrekkTilOppdrag(
                    dokument =
                        Document(
                            transaksjonsId = "TransaksjonsId01",
                            innrapporteringTrekk =
                                InnrapporteringTrekk(
                                    aksjonskode = Aksjonskode.NY,
                                    kilde = "SOKOSUTLEGG",
                                    navTrekkId = "",
                                    kreditorIdTss = "80000423362",
                                    kreditorTrekkId = "10342395",
                                    debitorId = "19074639472",
                                    kid = "17654202404",
                                    kreditorsRef = "SAK1",
                                    kodeTrekktype = "KRED",
                                    kodeTrekkAlternativ = TrekkAlternativ.LOPP,
                                    prioritetFomDato = null,
                                    perioder = null,
                                ),
                        ),
                    mmel = null,
                )

            val mockTransaksjonOS =
                mockk<TransaksjonOS> {
                    every { documentJson } returns jsonConfig.encodeToString(document)
                }
            val repositoryNy =
                mockk<RepositoryNy>(relaxed = true) {
                    every { getTransaksjonerTilOsSomIkkeErSendt() } returns listOf(mockTransaksjonOS)
                }
            val mockSlackService =
                mockk<SlackService> {
                    coEvery { sendCachedErrors(any()) } returns Unit
                    every { addError(any(), any()) } returns Unit
                }

            val utleggsTrekkService =
                UtleggsTrekkService(
                    repositoryNy = repositoryNy,
                    skeClient = mockk(relaxed = true),
                    slackService = mockSlackService,
                    mqProducer = mqProducerMock,
                )
            When("Det skjedd en feil") {
                every { mqProducerMock.send(any()) } throws Exception("Couldn't send document")

                Then("Vi sender en alarm på slack") {
                    utleggsTrekkService.schedule()

                    verify { mockSlackService.addError("Feil ved sending", "Feil ved sending av dokument til OS: Couldn't send document") }
                    coVerify { mockSlackService.sendCachedErrors(any()) }
                }
            }
        }
        afterSpec {
            clearAllMocks()
            unmockkObject(KotlinLogging)
        }
    })