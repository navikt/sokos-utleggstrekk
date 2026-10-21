package no.nav.sokos.utleggstrekk.service

import java.time.LocalDateTime

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.ranges.shouldBeIn
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotliquery.queryOf

import no.nav.sokos.utleggstrekk.client.SkeClient
import no.nav.sokos.utleggstrekk.config.jsonConfig
import no.nav.sokos.utleggstrekk.database.Repository
import no.nav.sokos.utleggstrekk.database.TestRepository.getTransaksjonTilOs
import no.nav.sokos.utleggstrekk.database.TestRepository.getTrekkFraSkatt
import no.nav.sokos.utleggstrekk.database.TestRepository.getTrekkFraSkattStatus
import no.nav.sokos.utleggstrekk.database.TestRepository.getTrekkFraSkattValideringsfeil
import no.nav.sokos.utleggstrekk.database.TestRepository.insertTransaksjonTilOs
import no.nav.sokos.utleggstrekk.database.TestRepository.klargjorTransaksjonForResending
import no.nav.sokos.utleggstrekk.database.model.KvitteringStatus
import no.nav.sokos.utleggstrekk.database.model.SkattTrekkStatus
import no.nav.sokos.utleggstrekk.database.model.TransaksjonOS
import no.nav.sokos.utleggstrekk.database.model.TransaksjonsStatus
import no.nav.sokos.utleggstrekk.domene.nav.Aksjonskode
import no.nav.sokos.utleggstrekk.domene.nav.Document
import no.nav.sokos.utleggstrekk.domene.nav.ErrorCategory
import no.nav.sokos.utleggstrekk.domene.nav.ErrorHeader
import no.nav.sokos.utleggstrekk.domene.nav.InnrapporteringTrekk
import no.nav.sokos.utleggstrekk.domene.nav.OSDto
import no.nav.sokos.utleggstrekk.domene.nav.TrekkAlternativ
import no.nav.sokos.utleggstrekk.domene.nav.TrekkTilOppdrag
import no.nav.sokos.utleggstrekk.domene.ske.Betalingsinformasjon
import no.nav.sokos.utleggstrekk.domene.ske.Trekkpaalegg
import no.nav.sokos.utleggstrekk.domene.ske.Trekkprosent
import no.nav.sokos.utleggstrekk.domene.ske.Trekkstatus
import no.nav.sokos.utleggstrekk.domene.ske.TrekkstorrelseForPeriode
import no.nav.sokos.utleggstrekk.listener.DBListener
import no.nav.sokos.utleggstrekk.listener.DBListener.repository
import no.nav.sokos.utleggstrekk.mq.JmsProducerService
import no.nav.sokos.utleggstrekk.unleash.UnleashIntegration
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

        fun mockedFeatureToggle(hentFraSkatt: Boolean = false, prosesserUtleggstrekk: Boolean = false, sendTilOs: Boolean = false) =
            mockk<UnleashIntegration> {
                every { isHentFraSKEEnabled() } returns hentFraSkatt
                every { isProsesserUtleggstrekkEnabled() } returns prosesserUtleggstrekk
                every { isSendTilOSEnabled() } returns sendTilOs
            }

        Given("Vi henter ett trekk fra SKE") {
            val mottaker = Betalingsinformasjon(betalingsmottaker = "971648199", kidnummer = "2345676", kontonummer = "70213997155")
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
                    repository,
                    skeClient = skeClientMock,
                    slackService = slackService,
                    mqProducer = mqProducerMock,
                )

            When("Trekk sendes") {
                utleggsTrekkService.schedule()

                Then("Skal trekk serialiseres riktig") {
                    capturedPayloads.forEach {
                        it shouldContain "SOKOSUTLEGG"
                        it shouldContain "TRK1"
                    }
                }
                Then("Skal trekkpaalegget lagres i databasen") {
                    val trekkFraSkatt = repository.getTrekkFraSkatt(trekkpaalegg.trekkid, trekkpaalegg.trekkversjon)
                    trekkFraSkatt.shouldNotBeNull()
                    repository.getTrekkFraSkattValideringsfeil(trekkFraSkatt.id).shouldBeNull()

                    withClue("Perioder skal lagres") {
                        val perioder = repository.getPerioderForTrekkVersjon(trekkFraSkatt.id)
                        perioder.size shouldBe 1
                        val periode = perioder.first()
                        periode.trekkprosent.shouldNotBeNull()
                        periode.trekkprosent shouldBe trekkPeriode.trekkprosent!!.trekkprosent
                        periode.trekkbeloep.shouldBeNull()
                        periode.trekkbeloep shouldBe trekkPeriode.trekkbeloep
                    }

                    withClue("Betalingsinformasjon skal lagres") {
                        val betalingsInformasjon = repository.getBetalingsinformasjonForTrekk(trekkFraSkatt.id)

                        betalingsInformasjon.shouldNotBeNull()
                        betalingsInformasjon.betalingsmottaker shouldBe mottaker.betalingsmottaker
                    }
                }

                Then("Skal transaksjon oppdateres") {
                    val transaksjoner = repository.getTransaksjonerTilOsForTrekkID(trekkpaalegg.trekkid)
                    transaksjoner.shouldNotBeEmpty()
                    transaksjoner.forEach { it.transaksjonStatus shouldBe TransaksjonsStatus.SENDT }
                }
            }
        }
        Given("Vi henter nye trekk fra SKE og det er 10 nye og maksantall er 4") {
            DBListener.clearDB()
            val mottaker = Betalingsinformasjon(betalingsmottaker = "971648199", kidnummer = "2345676", kontonummer = "70213997155")
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
                    repository,
                    skeClient = skeClientMock,
                    slackService = slackService,
                    maxAntall = 4,
                    mqProducer = mqProducerMock,
                    featureToggles = mockedFeatureToggle(hentFraSkatt = true),
                )

            When("Trekk hentes") {
                utleggsTrekkService.schedule()
                Then("Gjøres det ${parts.size} kall mot SkeClient helt til alle er nye utleggstrekk er hentet") {
                    coVerify(exactly = parts.size) { skeClientMock.hentUtleggstrekkFraSekvensnr(any()) }
                }
            }
        }

        Given("Det oppstå feil når vi henter ett trekk fra ske") {
            Then("Vi sender alarmer til slack hvis det stod en feil") {
                val slackService =
                    mockk<SlackService> {
                        coEvery { sendCachedErrors(any()) } returns Unit
                    }
                val utleggsTrekkService =
                    UtleggsTrekkService(
                        repository = mockk(relaxed = true),
                        skeClient = mockk(relaxed = true),
                        slackService = slackService,
                        mqProducer = mqProducerMock,
                        featureToggles = mockedFeatureToggle(hentFraSkatt = true),
                    )

                utleggsTrekkService.schedule()
                coVerify(exactly = 1) { slackService.sendCachedErrors(ErrorCategory.TREKK_HENTING) }
            }
        }

        Given("Vi henter transaksjoner til OS som mangler kvittering") {
            val now = LocalDateTime.now()
            listOf(
                Triple("nylig sendt", now, 0),
                Triple("like under ett døgn", now.minusDays(1).plusMinutes(1), 0),
                Triple("like over ett døgn", now.minusDays(1).minusMinutes(1), 1),
                Triple("to døgn gammel", now.minusDays(2), 1),
                Triple("uten sendetidspunkt", null, 0),
            ).forEach { (beskrivelse, sendetidspunkt, antallAlarmer) ->
                When("Transaksjonen er $beskrivelse") {
                    Then("Alarm sendes bare hvis sendetidspunktet er eldre enn ett døgn") {
                        val transaksjon =
                            mockk<TransaksjonOS> {
                                every { transaksjonsID } returns "alarmtest"
                                every { tidspunktSendt } returns sendetidspunkt
                            }
                        val mockedRepository =
                            mockk<Repository> {
                                every { getTransakjonerTilOsSomManglerKvittering() } returns listOf(transaksjon)
                            }
                        val slackService = mockk<SlackService>(relaxUnitFun = true)
                        val service =
                            UtleggsTrekkService(
                                mockedRepository,
                                skeClient = mockk(),
                                slackService = slackService,
                                mqProducer = mockk(),
                                featureToggles = mockedFeatureToggle(),
                            )

                        service.reportMissingKvittering()

                        verify(exactly = antallAlarmer) { slackService.addError(ErrorHeader.MANGLENDE_KVITTERING, any(), "alarmtest") }
                        coVerify(exactly = 1) { slackService.sendCachedErrors(ErrorCategory.KVITTERING_UTEBLIR) }
                    }
                }
            }
        }

        KvitteringStatus.entries.forEach { forrigeKvitteringStatus ->
            Given("En transaksjon med kvitteringsstatus $forrigeKvitteringStatus resendes gjennom sendeflyten") {
                fun klargjorResending(): TrekkTilOppdrag {
                    DBListener.clearDB()
                    val dokument =
                        jsonConfig
                            .decodeFromString<TrekkTilOppdrag>(resourceToString("mq/trekk_med_kvittering_ok/trekk1_ok_kvittering.json"))
                            .copy(mmel = null)
                    val transaksjonsId = dokument.dokument.transaksjonsId
                    repository.insertTransaksjonTilOs(
                        OSDto(
                            transaksjonID = transaksjonsId,
                            trekkIDSke = "ResendtTrekk",
                            trekkversjon = 1,
                            innrapporteringTrekk = dokument.dokument.innrapporteringTrekk,
                            documentJson = jsonConfig.encodeToString(dokument),
                        ),
                    )
                    repository.updateTransaksjonSendt(transaksjonsId, KvitteringStatus.IKKE_MOTTATT)
                    repository.updateReceiptStatusOfTransaksjon(transaksjonsId, forrigeKvitteringStatus, "navTrekkId")
                    repository.klargjorTransaksjonForResending(transaksjonsId, LocalDateTime.of(2024, 1, 1, 0, 0))
                    return dokument
                }

                When("MQ-sendingen lykkes") {
                    val dokument = klargjorResending()
                    val transaksjonsId = dokument.dokument.transaksjonsId
                    val slackService = mockk<SlackService>(relaxUnitFun = true)
                    val mqProducer = mockk<JmsProducerService>(relaxUnitFun = true)
                    val service =
                        UtleggsTrekkService(
                            repository,
                            skeClient = mockk(),
                            slackService = slackService,
                            mqProducer = mqProducer,
                            featureToggles = mockedFeatureToggle(sendTilOs = true),
                        )
                    val tidligsteSendetidspunkt =
                        repository.withTransaction { session ->
                            session.single(queryOf("SELECT NOW() AS tidspunkt")) { it.localDateTime("tidspunkt") }.shouldNotBeNull()
                        }
                    service.schedule()
                    val senesteSendetidspunkt =
                        repository.withTransaction { session ->
                            session.single(queryOf("SELECT NOW() AS tidspunkt")) { it.localDateTime("tidspunkt") }.shouldNotBeNull()
                        }
                    val transaksjon = repository.getTransaksjonTilOs(transaksjonsId).shouldNotBeNull()

                    Then("Samme dokument sendes og begge tidspunktene oppdateres") {
                        verify(exactly = 1) { mqProducer.send(jsonConfig.encodeToString(dokument)) }
                        transaksjon.transaksjonStatus shouldBe TransaksjonsStatus.SENDT
                        transaksjon.tidspunktSendt.shouldNotBeNull().shouldBeIn(tidligsteSendetidspunkt..senesteSendetidspunkt)
                        transaksjon.tidspunktSisteStatus.shouldNotBeNull().shouldBeIn(tidligsteSendetidspunkt..senesteSendetidspunkt)
                        verify(exactly = 0) { slackService.addError(any(), any(), any()) }
                    }

                    Then("Transaksjonen venter på ny kvittering uten umiddelbar alarm") {
                        service.reportMissingKvittering()
                        verify(exactly = 0) { slackService.addError(ErrorHeader.MANGLENDE_KVITTERING, any(), any()) }
                        transaksjon.kvitteringStatus shouldBe KvitteringStatus.IKKE_MOTTATT
                    }

                    Then("Alarm utløses etter ett døgn uten ny kvittering") {
                        repository.withTransaction { session ->
                            session.update(
                                queryOf(
                                    "UPDATE transaksjon_os SET tidspunkt_sendt = NOW() - INTERVAL '2 days' WHERE transaksjons_id = :id",
                                    mapOf("id" to transaksjonsId),
                                ),
                            ) shouldBe 1
                        }
                        service.reportMissingKvittering()
                        verify(exactly = 1) { slackService.addError(ErrorHeader.MANGLENDE_KVITTERING, any(), transaksjonsId) }
                    }

                    Then("Ny kvittering stopper alarmen uten å endre sendetidspunktet") {
                        clearMocks(slackService, answers = false)
                        val forKvittering = repository.getTransaksjonTilOs(transaksjonsId).shouldNotBeNull()
                        repository.updateReceiptStatusOfTransaksjon(transaksjonsId, KvitteringStatus.OK, "navTrekkId")
                        val kvittertTransaksjon = repository.getTransaksjonTilOs(transaksjonsId).shouldNotBeNull()
                        service.reportMissingKvittering()

                        kvittertTransaksjon.kvitteringStatus shouldBe KvitteringStatus.OK
                        kvittertTransaksjon.tidspunktSendt.shouldNotBeNull() shouldBe forKvittering.tidspunktSendt
                        repository.getTransakjonerTilOsSomManglerKvittering().map { it.transaksjonsID } shouldBe emptyList()
                        verify(exactly = 0) { slackService.addError(ErrorHeader.MANGLENDE_KVITTERING, any(), any()) }
                    }
                }

                When("MQ-sendingen feiler") {
                    val dokument = klargjorResending()
                    val transaksjonsId = dokument.dokument.transaksjonsId
                    val forSending = repository.getTransaksjonTilOs(transaksjonsId).shouldNotBeNull()
                    val slackService = mockk<SlackService>(relaxUnitFun = true)
                    val mqProducer =
                        mockk<JmsProducerService> {
                            every { send(any()) } throws IllegalStateException("MQ utilgjengelig")
                        }
                    val service =
                        UtleggsTrekkService(
                            repository,
                            skeClient = mockk(),
                            slackService = slackService,
                            mqProducer = mqProducer,
                            featureToggles = mockedFeatureToggle(sendTilOs = true),
                        )
                    service.schedule()

                    Then("Status og tidspunkter beholdes slik at transaksjonen kan forsøkes igjen") {
                        repository.getTransaksjonTilOs(transaksjonsId) shouldBe forSending
                        repository.getTransaksjonerTilOsSomIkkeErSendt().map { it.transaksjonsID } shouldBe listOf(transaksjonsId)
                        verify(exactly = 1) { mqProducer.send(jsonConfig.encodeToString(dokument)) }
                        verify(exactly = 1) { slackService.addError(ErrorHeader.FEIL_VED_SENDING, any(), transaksjonsId) }
                    }

                    Then("Manglende kvittering varsles ikke for den mislykkede resendingen") {
                        service.reportMissingKvittering()
                        verify(exactly = 0) { slackService.addError(ErrorHeader.MANGLENDE_KVITTERING, any(), any()) }
                    }
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
                    repository,
                    skeClient = skeClientMock,
                    slackService = slackService,
                    maxAntall = 2,
                    mqProducer = mqProducerMock,
                    featureToggles = mockedFeatureToggle(hentFraSkatt = true),
                )

            When("Trekket prosesseres") {
                utleggsTrekkService.schedule()
                Then("Blir status AVVIST") {
                    repository.getTrekkFraSkattStatus(1) shouldBe SkattTrekkStatus.AVVIST
                }
                Then("Lagres den opprinnelige valideringsfeilen og sendes til Slack") {
                    val trekkFraSkatt = repository.getTrekkFraSkatt(trekkpaalegg.trekkid, trekkpaalegg.trekkversjon).shouldNotBeNull()
                    val expected =
                        "Trekkpaalegg validation failed: trekkid: ${trekkpaalegg.trekkid}, trekkversjon: ${trekkpaalegg.trekkversjon}, " +
                            "sekvensnummer: ${trekkpaalegg.sekvensnummer}, message: skyldner har ulovlig verdi"
                    repository.getTrekkFraSkattValideringsfeil(trekkFraSkatt.id) shouldBe expected
                    verify(exactly = 1) {
                        slackService.addError(
                            ErrorHeader.FEIL_I_VALIDERING,
                            "Ugyldige verdier i trekk fra skatt med id ${trekkpaalegg.trekkid}: $expected",
                            trekkpaalegg.trekkid,
                        )
                    }
                }
            }
        }

        Given("Vi sender trekk til OS") {
            val mockSlackService =
                mockk<SlackService> {
                    coEvery { sendCachedErrors(any()) } returns Unit
                    every { addError(any(), any(), any()) } returns Unit
                }

            val mockedTransaksjonOS = mockk<TransaksjonOS>()

            val mockedRepository =
                mockk<Repository> {
                    every { getTransaksjonerTilOsSomIkkeErSendt() } returns listOf(mockedTransaksjonOS)
                    every { updateTransaksjonSendt(any(), any()) } returns Unit
                    every { updateTransaksjonValideringsfeil(any()) } returns Unit
                    every { deleteOldData() } returns Unit
                }

            val utleggsTrekkService =
                UtleggsTrekkService(
                    repository = mockedRepository,
                    skeClient = mockk(),
                    slackService = mockSlackService,
                    mqProducer = mqProducerMock,
                    featureToggles = mockedFeatureToggle(sendTilOs = true),
                )

            fun trekkTilOppdrag(shouldValidate: Boolean = true): TrekkTilOppdrag {
                val innrapporteringTrekk =
                    InnrapporteringTrekk(
                        aksjonskode = Aksjonskode.NY,
                        kilde = "SOKOSUTLEGG",
                        navTrekkId = "",
                        kreditorIdTss = if (shouldValidate) "80000423362" else "invalid kreditorIdTss",
                        kreditorTrekkId = "10342395",
                        debitorId = "19074639472",
                        kid = "2191507714",
                        kreditorsRef = "SAK1",
                        kodeTrekktype = "KRED",
                        kodeTrekkAlternativ = TrekkAlternativ.LOPP,
                        prioritetFomDato = null,
                        perioder = null,
                    )

                return TrekkTilOppdrag(
                    dokument =
                        Document(
                            transaksjonsId = "TransaksjonsId01",
                            innrapporteringTrekk = innrapporteringTrekk,
                        ),
                    mmel = null,
                )
            }

            When("Transaksjonen sendes ikke") {
                clearMocks(mockSlackService, answers = false)
                val trekkTilOppdrag = trekkTilOppdrag()
                every { mockedTransaksjonOS.documentJson } returns jsonConfig.encodeToString(trekkTilOppdrag)
                every { mockedTransaksjonOS.transaksjonsID } returns trekkTilOppdrag.dokument.transaksjonsId
                every { mqProducerMock.send(any()) } throws Exception("Couldn't send document")

                utleggsTrekkService.schedule()
                Then("Sendes en alarm til slack") {
                    verify { mockSlackService.addError(ErrorHeader.FEIL_VED_SENDING, "Feil ved sending av dokument til OS", "TransaksjonsId01") }
                    coVerify { mockSlackService.sendCachedErrors(any()) }
                }

                Then("Vi setter ikke transaksjons status til VALIDERINGSFEIL") {
                    verify(exactly = 0) { mockedRepository.updateTransaksjonValideringsfeil(any()) }
                }
            }

            When("Transaksjonen valideres ikke") {
                clearMocks(mockSlackService, answers = false)
                val trekkTilOppdrag = trekkTilOppdrag(shouldValidate = false)
                every { mockedTransaksjonOS.documentJson } returns jsonConfig.encodeToString(trekkTilOppdrag)
                every { mockedTransaksjonOS.transaksjonsID } returns trekkTilOppdrag.dokument.transaksjonsId

                utleggsTrekkService.schedule()
                Then("Vi sender en alarm på slack") {
                    verify { mockSlackService.addError(ErrorHeader.FEIL_VED_SENDING, "Feil ved sending av dokument til OS", "TransaksjonsId01") }
                    coVerify { mockSlackService.sendCachedErrors(any()) }
                }

                Then("Vi setter transaksjons status til VALIDERINGSFEIL") {
                    verify(exactly = 1) { mockedRepository.updateTransaksjonValideringsfeil("TransaksjonsId01") }
                }
            }

            When("MQ-sendingen lykkes, men databaseoppdateringen feiler") {
                clearMocks(mockSlackService, mockedRepository, mqProducerMock, answers = false)
                val trekkTilOppdrag = trekkTilOppdrag()
                every { mockedTransaksjonOS.documentJson } returns jsonConfig.encodeToString(trekkTilOppdrag)
                every { mockedTransaksjonOS.transaksjonsID } returns trekkTilOppdrag.dokument.transaksjonsId
                every { mqProducerMock.send(any()) } returns Unit
                every { mockedRepository.updateTransaksjonSendt(any(), any()) } throws IllegalStateException("Database utilgjengelig")

                utleggsTrekkService.schedule()

                Then("Databasefeilen varsles selv om dokumentet er sendt til MQ") {
                    verify(exactly = 1) { mqProducerMock.send(jsonConfig.encodeToString(trekkTilOppdrag)) }
                    verify(exactly = 1) { mockedRepository.updateTransaksjonSendt("TransaksjonsId01", any()) }
                    verify(exactly = 1) {
                        mockSlackService.addError(ErrorHeader.DATABASE_ERROR, "Kunne ikke oppdatere transaksjon status", "TransaksjonsId01")
                    }
                    verify(exactly = 0) { mockSlackService.addError(ErrorHeader.FEIL_VED_SENDING, any(), any()) }
                    verify(exactly = 0) { mockedRepository.updateTransaksjonValideringsfeil(any()) }
                }
            }
        }

        afterSpec {
            clearAllMocks()
        }
    })
