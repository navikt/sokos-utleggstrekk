package no.nav.sokos.utleggstrekk.client

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
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
import kotlinx.serialization.json.Json
import mu.KLogger
import mu.KotlinLogging
import no.nav.sokos.utleggstrekk.domene.ske.Betalingsinformasjon
import no.nav.sokos.utleggstrekk.domene.ske.Trekkbeloep
import no.nav.sokos.utleggstrekk.domene.ske.Trekkpaalegg
import no.nav.sokos.utleggstrekk.domene.ske.Trekkprosent
import no.nav.sokos.utleggstrekk.domene.ske.TrekkstorrelseForPeriode
import no.nav.sokos.utleggstrekk.security.maskinporten.MaskinportenAccessTokenClient
import no.nav.sokos.utleggstrekk.util.resourceToString
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class SkeClientTest :
    FunSpec({
        val logger =
            mockk<KLogger> {
                every { error(any<() -> Unit>()) } just runs
            }

        val mockToken = "mock-token"
        val mockTokenProvider =
            mockk<MaskinportenAccessTokenClient> {
                coEvery { hentAccessToken() } returns mockToken
            }

        val headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

        val mockTrekk =
            Trekkpaalegg(
                trekkid = "1",
                sekvensnummer = 1,
                trekkversjon = 1,
                opprettet = Instant.parse("2024-06-16T13:33:05.672Z"),
                saksnummer = "sak-2023-899",
                trekkpliktig = "889640782",
                skyldner = "19628198007",
                trekkstatus = "aktiv",
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
                        betalingsmottaker = "971648198",
                        kidnummer = "17654202404",
                        kontonummer = "76940512057",
                    ),
            )

        beforeSpec {
            mockkObject(KotlinLogging)
            every { KotlinLogging.logger(any<() -> Unit>()) } returns logger
        }

        context("hentAlleUtleggstrekk") {
            test("hentAlleUtleggstrekk skal sende GET request med korrekt headers og body") {
                val engine =
                    MockEngine {
                        respond(content = "[]", headers = headers)
                    }
                val skeClient = SkeClient(mockClient(engine), mockTokenProvider)

                val trekkListe = skeClient.hentAlleUtleggstrekk()

                engine.requestHistory shouldHaveSize 1

                val request = engine.requestHistory.first()
                request.url.toString() shouldNotContain "\\?.*".toRegex()
                request.headers["Klientid"] shouldBe "NAV/0.1"
                request.headers["Korrelasjonsid"] shouldNotBe null
                request.headers[HttpHeaders.Authorization] shouldBe "Bearer mock-token"

                trekkListe shouldBe emptyList()
                verify(exactly = 0) { logger.error(any<() -> Unit>()) }
            }

            test("hentAlleUtleggstrekk skal konvertere en body til en list av Trekkpaalegg") {
                val mockedResponse = resourceToString("FraSkatt_Trekkversjon1_1Trekkalternativ-2trekk.json")
                val engine =
                    MockEngine {
                        respond(content = mockedResponse, headers = headers)
                    }
                val skeClient = SkeClient(mockClient(engine), mockTokenProvider)

                val trekkListe = skeClient.hentAlleUtleggstrekk()

                trekkListe shouldHaveSize 2
                trekkListe.first() shouldBe mockTrekk
                verify(exactly = 0) { logger.error(any<() -> Unit>()) }
            }

            test("hentAlleUtleggstrekk skal return en emptyList når den kan ikke parse body") {
                val mockedResponse = resourceToString("Fra_Skatt_Trekk1_versjon1_beløp_ingen_ting_ekstra_Feil.json")
                val mockEngine =
                    MockEngine {
                        respond(content = mockedResponse, headers = headers)
                    }
                val skeClient = SkeClient(mockClient(mockEngine), mockTokenProvider)

                val errorMsg = slot<() -> Any?>()
                every { logger.error(capture(errorMsg)) } just runs

                val trekkListe = skeClient.hentAlleUtleggstrekk()

                trekkListe shouldBe emptyList()
                verify(exactly = 1) { logger.error(any<() -> Unit>()) }
                errorMsg.captured.invoke().toString() shouldContain "Feil i konvertering av response"
            }

            test("hentAlleUtleggstrekk skal takle data med felter den ikke kjenner") {
                val mockedResponse = resourceToString("Trekk_med_ukjente_felter.json")
                val mockEngine =
                    MockEngine {
                        respond(content = mockedResponse, headers = headers)
                    }
                val skeClient = SkeClient(mockClient(mockEngine), mockTokenProvider)

                val trekkListe = skeClient.hentAlleUtleggstrekk()
                trekkListe shouldHaveSize 1
                verify(exactly = 0) { logger.error(any<() -> Unit>()) }
            }
        }

        context("hentUtleggstrekkFraSekvensnr") {
            test("hentUtleggstrekkFraSekvensnr skal sende GET request med korrekt headers og body") {
                val engine =
                    MockEngine {
                        respond(content = "[]", headers = headers)
                    }
                val skeClient = SkeClient(mockClient(engine), mockTokenProvider)

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
                verify(exactly = 0) { logger.error(any<() -> Unit>()) }
            }
        }

        afterTest {
            clearMocks(logger)
        }

        afterSpec {
            clearAllMocks()
            unmockkObject(KotlinLogging)
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
