package no.nav.sokos.utleggstrekk.client

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.config.ApplicationConfig
import io.mockk.clearAllMocks
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.slf4j.LoggerFactory

import no.nav.sokos.utleggstrekk.config.PropertiesConfig
import no.nav.sokos.utleggstrekk.config.TEAM_LOGS_MARKER
import no.nav.sokos.utleggstrekk.config.jsonConfig
import no.nav.sokos.utleggstrekk.domene.nav.ErrorHeader
import no.nav.sokos.utleggstrekk.domene.ske.Betalingsinformasjon
import no.nav.sokos.utleggstrekk.domene.ske.SkeErrorMessage
import no.nav.sokos.utleggstrekk.domene.ske.Trekkbeloep
import no.nav.sokos.utleggstrekk.domene.ske.Trekkpaalegg
import no.nav.sokos.utleggstrekk.domene.ske.Trekkprosent
import no.nav.sokos.utleggstrekk.domene.ske.Trekkstatus.AKTIV
import no.nav.sokos.utleggstrekk.domene.ske.TrekkstorrelseForPeriode
import no.nav.sokos.utleggstrekk.security.maskinporten.MaskinportenAccessTokenClient
import no.nav.sokos.utleggstrekk.service.SlackService
import no.nav.sokos.utleggstrekk.util.MockHttpClient.getClient
import no.nav.sokos.utleggstrekk.util.MockHttpClient.getEngine
import no.nav.sokos.utleggstrekk.util.resourceToString

class SkeClientTest :
    FunSpec({
        val slackService = mockk<SlackService>(relaxUnitFun = true)
        val skeServiceLogger = LoggerFactory.getLogger(SkeClient::class.java) as Logger

        val logAppender = ListAppender<ILoggingEvent>()

        beforeTest {
            logAppender.start()
            skeServiceLogger.addAppender(logAppender)
        }
        val mockToken = "mock-token"
        val mockTokenProvider =
            mockk<MaskinportenAccessTokenClient> {
                coEvery { getAccessToken() } returns mockToken
            }

        val mockTrekk =
            Trekkpaalegg(
                trekkid = "1",
                sekvensnummer = 1,
                trekkversjon = 1,
                opprettet = "2024-06-16T13:33:05.672Z",
                saksnummer = "sak-2023-899",
                trekkpliktig = "889640782",
                skyldner = "19628198007",
                trekkstatus = AKTIV,
                trekkstoerrelseForPeriode =
                    listOf(
                        TrekkstorrelseForPeriode(
                            startdato = "2023-06-13",
                            sluttdato = "2024-11-30",
                            trekkbeloep = Trekkbeloep(5000.0),
                        ),
                        TrekkstorrelseForPeriode(
                            startdato = "2024-12-01",
                            sluttdato = "2024-12-31",
                            trekkprosent = Trekkprosent(5.0),
                        ),
                        TrekkstorrelseForPeriode(
                            startdato = "2025-01-01",
                            trekkbeloep = Trekkbeloep(2000.0),
                        ),
                    ),
                betalingsinformasjon =
                    Betalingsinformasjon(
                        betalingsmottaker = "971648199",
                        kidnummer = "2191507714",
                        kontonummer = "70213997155",
                    ),
            )

        beforeSpec {
            mockkObject(PropertiesConfig)
            every { PropertiesConfig.config } returns ApplicationConfig("application-test.conf")
        }

        context("hentUtleggstrekkFraSekvensnr") {
            test("skal sende GET request med korrekt headers og body") {
                val mockEngine = getEngine()
                val skeClient = SkeClient(getClient(mockEngine), slackService, mockTokenProvider)

                val sekvensnr = 1
                val trekkListe = skeClient.hentUtleggstrekkFraSekvensnr(sekvensnr)

                mockEngine.requestHistory shouldHaveSize 1

                val request = mockEngine.requestHistory.first()
                println(request.url.encodedPath)
                request.url.toString() shouldContain "\\?fraSekvensnummer=$sekvensnr&maksAntall=2500".toRegex()
                request.headers["Klientid"] shouldBe "NAV/0.1"
                request.headers["Korrelasjonsid"] shouldNotBe null
                request.headers[HttpHeaders.Authorization] shouldBe "Bearer mock-token"

                trekkListe shouldBe emptyList()
                logAppender.list.filter { it.level == Level.WARN }.size shouldBe 0
            }

            test("skal konvertere en body til en list av Trekkpaalegg") {
                val mockedResponse = resourceToString("FraSkatt_Trekkversjon1_1Trekkalternativ-2trekk.json")
                val mockEngine = getEngine(mockedResponse)
                val skeClient = SkeClient(getClient(mockEngine), slackService, mockTokenProvider)

                val trekkListe = skeClient.hentUtleggstrekkFraSekvensnr(1)

                trekkListe shouldHaveSize 2
                trekkListe.first() shouldBe mockTrekk

                logAppender.list.filter { it.level == Level.WARN }.size shouldBe 0
            }

            test("skal returnere en emptyList når den ikke kan parse body ") {
                val mockedResponse = resourceToString("Fra_Skatt_Trekk1_versjon1_beløp_ingen_ting_ekstra_Feil.json")
                val mockEngine = getEngine(mockedResponse)
                val skeClient = SkeClient(getClient(mockEngine), slackService, mockTokenProvider)

                val trekkListe = skeClient.hentUtleggstrekkFraSekvensnr(1)

                logAppender.list.filter { it.level == Level.ERROR }.size shouldBe 2 // én med og én uten TEAM_LOGS_MARKER
                logAppender.list.filter { it.message == "Feil i konvertering av response til Trekkpålegg " }.size shouldBe 1
                logAppender.list.filter { it.level == Level.WARN }.size shouldBe 0

                trekkListe shouldBe emptyList()
            }

            test("skal logge warn når API-kallet returnerer en empty body") {
                val mockEngine = getEngine("")
                val skeClient = SkeClient(getClient(mockEngine), slackService, mockTokenProvider)

                val trekkListe = skeClient.hentUtleggstrekkFraSekvensnr(1)

                trekkListe.shouldBeEmpty()
                logAppender.list.filter { it.level == Level.WARN }.size shouldBe 1
            }

            test("skal sende slack + logge TEAM_LOGS når API-kallet gir 4xx, og ikke sende slack når 5xx") {
                val alarmHeaders = mutableListOf<ErrorHeader>()
                val messages = mutableListOf<String>()
                every { slackService.addError(capture(alarmHeaders), capture(messages)) } returns Unit

                val skeErrorMessage1 =
                    SkeErrorMessage(
                        kode = "KB-005",
                        melding = "Feil i forbindelse med samtykketoken.",
                        korrelasjonsid = "123",
                    )
                val skeErrorMessage2 =
                    SkeErrorMessage(
                        kode = "KB-001",
                        melding = "Uventet feil på tjenesten",
                        korrelasjonsid = "345",
                    )

                val mockEngine =
                    getEngine(
                        Pair(HttpStatusCode.Forbidden, jsonConfig.encodeToString(skeErrorMessage1)),
                        Pair(HttpStatusCode.InternalServerError, jsonConfig.encodeToString(skeErrorMessage2)),
                    )

                val skeClient = SkeClient(getClient(mockEngine), slackService, mockTokenProvider)

                skeClient.hentUtleggstrekkFraSekvensnr(1).shouldBeEmpty()
                skeClient.hentUtleggstrekkFraSekvensnr(2).shouldBeEmpty()

                // Oppdatert behavior: Slack sendes både for 4xx og 5xx (hvis responsen kan parses til SkeErrorMessage).
                verify(exactly = 2) { slackService.addError(any<ErrorHeader>(), any<String>()) }
                alarmHeaders.first() shouldBe ErrorHeader.FEIL_FRA_SKE
                messages.first() shouldContain "sekvensnr=1"
                messages.first() shouldContain "KB-005"

                alarmHeaders.last() shouldBe ErrorHeader.FEIL_FRA_SKE
                messages.last() shouldContain "sekvensnr=2"
                messages.last() shouldContain "KB-001"

                // Logging: TEAM_LOGS marker error logges for begge kallene.
                logAppender.list.filter { it.markerList?.contains(TEAM_LOGS_MARKER) ?: false }.size shouldBe 2
            }
        }

        context(name = "Feil i json-struktur") {
            test("String i numerisk id skal returnere tom liste`") {
                val mockedResponse = resourceToString("trekkMedFeil/stringForTall.json")
                val mockEngine = getEngine(mockedResponse)
                val skeClient = SkeClient(getClient(mockEngine), slackService, mockTokenProvider)

                skeClient.hentUtleggstrekkFraSekvensnr(1).shouldBeEmpty()
            }

            test("Beløp og prosent i samme periode skal kaste exception") {
                val mockedResponse = resourceToString("trekkMedFeil/belopOgProsent.json")
                val mockEngine = getEngine(mockedResponse)
                val skeClient = SkeClient(getClient(mockEngine), slackService, mockTokenProvider)

                shouldThrow<Exception> {
                    skeClient.hentUtleggstrekkFraSekvensnr(1).first()
                }
            }
            test("Trekk med ugyldige tegn skal returnere tom liste") {
                val mockedResponse = resourceToString("trekkMedFeil/trekkMedUgyldigPnr.json").replace(" ", "\u0001")

                val mockEngine = getEngine(mockedResponse)
                val skeClient = SkeClient(getClient(mockEngine), slackService, mockTokenProvider)

                skeClient.hentUtleggstrekkFraSekvensnr(1).shouldBeEmpty()
            }
        }

        afterTest {
            clearMocks(slackService)
            skeServiceLogger.detachAppender(logAppender)
            logAppender.list.clear()
            logAppender.stop()
        }

        afterSpec {
            clearAllMocks()
            unmockkObject(PropertiesConfig)
        }
    })
