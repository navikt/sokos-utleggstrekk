package no.nav.sokos.utleggstrekk.client

import kotlin.time.ExperimentalTime
import kotlinx.serialization.json.Json

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.ApplicationConfig
import io.mockk.clearAllMocks
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import mu.KLogger
import mu.KotlinLogging

import no.nav.sokos.utleggstrekk.config.PropertiesConfig
import no.nav.sokos.utleggstrekk.config.jsonConfig
import no.nav.sokos.utleggstrekk.domene.ske.Betalingsinformasjon
import no.nav.sokos.utleggstrekk.domene.ske.SkeErrorMessage
import no.nav.sokos.utleggstrekk.domene.ske.Trekkbeloep
import no.nav.sokos.utleggstrekk.domene.ske.Trekkpaalegg
import no.nav.sokos.utleggstrekk.domene.ske.Trekkprosent
import no.nav.sokos.utleggstrekk.domene.ske.Trekkstatus.AKTIV
import no.nav.sokos.utleggstrekk.domene.ske.TrekkstorrelseForPeriode
import no.nav.sokos.utleggstrekk.security.maskinporten.MaskinportenAccessTokenClient
import no.nav.sokos.utleggstrekk.service.SlackService
import no.nav.sokos.utleggstrekk.util.resourceToString

@OptIn(ExperimentalTime::class)
class SkeClientTest :
    FunSpec({
        val slackService = mockk<SlackService>(relaxUnitFun = true)
        val logger =
            mockk<KLogger> {
                every { warn(any<() -> Unit>()) } just runs
            }

        val mockToken = "mock-token"
        val mockTokenProvider =
            mockk<MaskinportenAccessTokenClient> {
                coEvery { getAccessToken() } returns mockToken
            }

        val headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

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
                        kidnummer = "17654202404",
                        kontonummer = "70213997155",
                    ),
            )

        beforeSpec {
            mockkObject(KotlinLogging, PropertiesConfig)
            every { KotlinLogging.logger(any<() -> Unit>()) } returns logger
            every { PropertiesConfig.config } returns ApplicationConfig("application-test.conf")
        }

        context("hentUtleggstrekkFraSekvensnr") {
            test("skal sende GET request med korrekt headers og body") {
                val engine =
                    MockEngine {
                        respond(content = "[]", headers = headers)
                    }
                val skeClient = SkeClient(mockClient(engine), slackService, mockTokenProvider)

                val sekvensnr = 1
                val trekkListe = skeClient.hentUtleggstrekkFraSekvensnr(sekvensnr)

                engine.requestHistory shouldHaveSize 1

                val request = engine.requestHistory.first()
                println(request.url.encodedPath)
                request.url.toString() shouldContain "\\?fraSekvensnummer=$sekvensnr&maksAntall=2500".toRegex()
                request.headers["Klientid"] shouldBe "NAV/0.1"
                request.headers["Korrelasjonsid"] shouldNotBe null
                request.headers[HttpHeaders.Authorization] shouldBe "Bearer mock-token"

                trekkListe shouldBe emptyList()
                verify(exactly = 0) { logger.warn(any<() -> Unit>()) }
            }

            test("skal konvertere en body til en list av Trekkpaalegg") {
                val mockedResponse = resourceToString("FraSkatt_Trekkversjon1_1Trekkalternativ-2trekk.json")
                val engine =
                    MockEngine {
                        respond(content = mockedResponse, headers = headers)
                    }
                val skeClient = SkeClient(mockClient(engine), slackService, mockTokenProvider)

                val trekkListe = skeClient.hentUtleggstrekkFraSekvensnr(1)

                trekkListe shouldHaveSize 2
                trekkListe.first() shouldBe mockTrekk
                verify(exactly = 0) {
                    logger.warn(any<() -> Unit>())
                    slackService.addError(any(), any())
                }
            }

            test("skal returnere en emptyList når den kan ikke parse body og sende en alarm") {

                val mockedResponse = resourceToString("Fra_Skatt_Trekk1_versjon1_beløp_ingen_ting_ekstra_Feil.json")
                val mockEngine =
                    MockEngine {
                        respond(content = mockedResponse, headers = headers)
                    }
                val skeClient = SkeClient(mockClient(mockEngine), slackService, mockTokenProvider)

                val errorMsg = slot<() -> Any?>()
                every { logger.warn(capture(errorMsg)) } just runs

                val slackMessage = slot<String>()
                every { slackService.addError(any(), capture(slackMessage)) } returns Unit

                val trekkListe = skeClient.hentUtleggstrekkFraSekvensnr(1)

                trekkListe shouldBe emptyList()
                verify(exactly = 1) {
                    logger.warn(any<() -> Unit>())
                    slackService.addError("JsonConvertException", any())
                }
                errorMsg.captured.invoke().toString() shouldContain "Feil i konvertering av response"
                slackMessage.captured shouldContain "Feil i konvertering av response"
            }

            test("skal takle data med felter den ikke kjenner") {
                val mockedResponse = resourceToString("Trekk_med_ukjente_felter.json")
                val mockEngine =
                    MockEngine {
                        respond(content = mockedResponse, headers = headers)
                    }
                val skeClient = SkeClient(mockClient(mockEngine), slackService, mockTokenProvider)

                val trekkListe = skeClient.hentUtleggstrekkFraSekvensnr(1)
                trekkListe shouldHaveSize 1
                verify(exactly = 0) {
                    logger.warn(any<() -> Unit>())
                    slackService.addError(any(), any())
                }
            }

            test("skal sende en alarm når API-kallet returnere en empty body") {
                val message = slot<String>()
                every { slackService.addError(any(), capture(message)) } returns Unit

                val engine =
                    MockEngine {
                        respond(content = "[]", headers = headers)
                    }
                val skeClient = SkeClient(mockClient(engine), slackService, mockTokenProvider)

                val sekvensnr = 1
                val trekkListe = skeClient.hentUtleggstrekkFraSekvensnr(sekvensnr)

                trekkListe.shouldBeEmpty()
                verify(exactly = 1) { slackService.addError("Manglende data", any()) }
                message.captured shouldContain "Fikk ingen data for sekvensnummer=$sekvensnr"
            }

            test("skal sende en alarm når API-kallet mislykkes") {
                val alarmHeaders = mutableListOf<String>()
                val messages = mutableListOf<String>()
                every { slackService.addError(capture(alarmHeaders), capture(messages)) } returns Unit

                val engineConfig =
                    MockEngineConfig().apply {
                        addHandler {
                            val content =
                                SkeErrorMessage(
                                    kode = "KB-005",
                                    melding = "Feil i forbindelse med samtykketoken.",
                                    korrelasjonsid = "123",
                                )
                            respondError(
                                status = HttpStatusCode.Forbidden,
                                content = jsonConfig.encodeToString(content),
                                headers = headers,
                            )
                        }

                        addHandler {
                            val content =
                                SkeErrorMessage(
                                    kode = "KB-001",
                                    melding = "Uventet feil på tjenesten",
                                    korrelasjonsid = "345",
                                )
                            respondError(
                                status = HttpStatusCode.InternalServerError,
                                content = jsonConfig.encodeToString(content),
                                headers = headers,
                            )
                        }
                    }
                val engine = MockEngine(engineConfig)

                val skeClient = SkeClient(mockClient(engine), slackService, mockTokenProvider)

                val trekkListe1 = skeClient.hentUtleggstrekkFraSekvensnr(1)
                val trekkListe2 = skeClient.hentUtleggstrekkFraSekvensnr(2)

                trekkListe1.shouldBeEmpty()
                trekkListe2.shouldBeEmpty()
                verify(exactly = 2) { slackService.addError(any(), any()) }

                alarmHeaders.first() shouldBe "403 Forbidden"
                messages.first() shouldContain ".+sekvensnr=1.+KB-005.+KorrelasjonsId.+".toRegex()

                alarmHeaders.last() shouldBe "500 Internal Server Error"
                messages.last() shouldContain ".+sekvensnr=2.+KB-001.+KorrelasjonsId.+".toRegex()
            }
        }

        context(name = "Feil i json-struktur") {
            test("String for tall id") {
                val mockedResponse = resourceToString("trekkMedFeil/stringForTall.json")
                val engine =
                    MockEngine {
                        respond(content = mockedResponse, headers = headers)
                    }
                val skeClient = SkeClient(mockClient(engine), slackService, mockTokenProvider)
                shouldThrow<Exception> {
                    skeClient.hentUtleggstrekkFraSekvensnr(1)
                }
            }
            test("Beløp og prosent i samme periode") {
                val mockedResponse = resourceToString("trekkMedFeil/belopOgProsent.json")
                val engine =
                    MockEngine {
                        respond(content = mockedResponse, headers = headers)
                    }
                val skeClient = SkeClient(mockClient(engine), slackService, mockTokenProvider)
                shouldThrow<Exception> {
                    val trekkListe = skeClient.hentUtleggstrekkFraSekvensnr(1)
                    println(trekkListe.first().trekkstoerrelseForPeriode.first())
                }
            }
        }

        afterTest {
            clearMocks(logger, slackService)
        }

        afterSpec {
            clearAllMocks()
            unmockkObject(KotlinLogging, PropertiesConfig)
        }
    })

private fun mockClient(engine: MockEngine) =
    HttpClient(engine) {
        install(ContentNegotiation) {
            json(
                Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                },
            )
        }
        expectSuccess = false
    }