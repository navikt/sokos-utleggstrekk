package no.nav.sokos.utleggstrekk.domene.ske

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.server.config.ApplicationConfig
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject

import no.nav.sokos.utleggstrekk.config.PropertiesConfig
import no.nav.sokos.utleggstrekk.domene.nav.Aksjonskode
import no.nav.sokos.utleggstrekk.domene.nav.InnrapporteringTrekk
import no.nav.sokos.utleggstrekk.domene.nav.Periode
import no.nav.sokos.utleggstrekk.domene.nav.Perioder
import no.nav.sokos.utleggstrekk.domene.nav.TrekkAlternativ
import no.nav.sokos.utleggstrekk.domene.nav.validate

class ValidationTest :
    BehaviorSpec({
        val trekkpaalegg =
            Trekkpaalegg(
                trekkid = "fe0d1de1-9840-4480-9382-3dbad27e9002",
                sekvensnummer = 1,
                trekkversjon = 1,
                opprettet = "2026-01-01T11:22:33Z",
                saksnummer = "UTLEGG/2025/678",
                trekkpliktig = "889640782",
                skyldner = "10987654321",
                Trekkstatus.AKTIV,
                trekkstoerrelseForPeriode = listOf(TrekkstorrelseForPeriode("2026-02-23", null, Trekkbeloep(2000.0), null)),
                betalingsinformasjon = Betalingsinformasjon("971648199", "17274826482648264826482", "70213997155"),
            )

        val utleggstrekk by lazy {
            InnrapporteringTrekk(
                aksjonskode = Aksjonskode.NY,
                navTrekkId = "1234",
                kreditorIdTss = PropertiesConfig.skeConfig.skeTSSId,
                kreditorTrekkId = "fe0d1de19840448093823dbad27e9002P",
                kreditorsRef = "UTLEGG/2025/678",
                debitorId = "10987654321",
                kodeTrekktype = "TRK1",
                kodeTrekkAlternativ = TrekkAlternativ.LOPP,
                kid = "17274826482648264826482",
                kilde = "SOKOSUTLEGG",
                saldo = 0.0,
                prioritetFomDato = "2025-12-02",
                gyldigTomDato = null,
                perioder = Perioder(listOf(Periode("2025-12-01", "2025-12-31", sats = 2000.0))),
            )
        }

        beforeSpec {
            mockkObject(PropertiesConfig)
            every { PropertiesConfig.config } returns ApplicationConfig("application-test.conf")
        }

        Given("Trekkpålegg med gyldige feltverdier") {
            When("Validering") {
                Then("Validate kaster ikke exception") {
                    trekkpaalegg.validate()
                }
            }
        }
        Given("Trekkpålegg med ugyldige feltverdier") {
            val ugyldige =
                listOf(
                    trekkpaalegg.copy(trekkid = "1232\t") to "Trekkpaalegg validation failed: trekkid: 1232\t, trekkversjon: 1, sekvensnummer: 1, message: trekkid har ugyldige tegn",
                    trekkpaalegg.copy(sekvensnummer = -1) to "message: sekvensnummer har ulovlig verdi",
                    trekkpaalegg.copy(trekkversjon = -1) to "message: trekkversjon har ulovlig verdi",
                    trekkpaalegg.copy(opprettet = "2026-01-01") to "message: opprettet har ulovlig verdi",
                    trekkpaalegg.copy(saksnummer = "sak\n") to "message: saksnummer har ugyldige tegn",
                    trekkpaalegg.copy(trekkpliktig = "trekkpliktig") to "message: trekkpliktig har ulovlig verdi",
                    trekkpaalegg.copy(skyldner = "bob") to "message: skyldner har ulovlig verdi",
                    trekkpaalegg.copy(trekkstoerrelseForPeriode = listOf(trekkpaalegg.trekkstoerrelseForPeriode.first().copy(startdato = "342"))) to "message: Startdato har ulovlig verdi",
                    trekkpaalegg.copy(trekkstoerrelseForPeriode = listOf(trekkpaalegg.trekkstoerrelseForPeriode.first().copy(sluttdato = "342"))) to "message: Sluttdato har ulovlig verdi",
                    trekkpaalegg.copy(trekkstoerrelseForPeriode = listOf(trekkpaalegg.trekkstoerrelseForPeriode.first().copy(trekkbeloep = Trekkbeloep(-1000.0)))) to
                        "message: Trekkbeloep ulovlig verdi",
                    trekkpaalegg.copy(trekkstoerrelseForPeriode = listOf(trekkpaalegg.trekkstoerrelseForPeriode.first().copy(trekkbeloep = null, trekkprosent = Trekkprosent(110.0)))) to
                        "message: Trekkprosent har ulovlig verdi",
                    trekkpaalegg.copy(trekkstoerrelseForPeriode = listOf(trekkpaalegg.trekkstoerrelseForPeriode.first().copy(trekkbeloep = null, trekkprosent = Trekkprosent(-20.0)))) to
                        "message: Trekkprosent har ulovlig verdi",
                    trekkpaalegg.copy(betalingsinformasjon = trekkpaalegg.betalingsinformasjon.copy(betalingsmottaker = "1123131111221")) to "message: Betalingsmottaker har ulovlig verdi",
                    trekkpaalegg.copy(betalingsinformasjon = trekkpaalegg.betalingsinformasjon.copy(kidnummer = "112hfd1")) to "message: kidnummer har ulovlig verdi",
                    trekkpaalegg.copy(betalingsinformasjon = trekkpaalegg.betalingsinformasjon.copy(kontonummer = "konto")) to "message: kontonummer har ulovlig verdi",
                )
            Then("Validate kaster exception") {
                ugyldige.forEachIndexed { i, (ugyldigTrekk, expected) ->
                    withClue("TREKK nr. $i, $ugyldigTrekk skal ikke validere") {
                        val exception = shouldThrow<IllegalArgumentException> { ugyldigTrekk.validate() }
                        exception.message shouldContain expected
                    }
                }
            }
        }
        Given("Trekkpålegg med periode hvor start og slutt dato er like") {
            When("Validering") {
                Then("Validate kaster ikke exception") {
                    val trekkpaalegg =
                        trekkpaalegg.copy(
                            trekkstoerrelseForPeriode =
                                listOf(trekkpaalegg.trekkstoerrelseForPeriode.first().copy(sluttdato = trekkpaalegg.trekkstoerrelseForPeriode.first().startdato)),
                        )
                    trekkpaalegg.validate()
                }
            }
        }
        Given("Utleggstrekk med gyldige feltverdier") {
            When("Validering") {
                Then("Validate kaster ikke exception") {
                    utleggstrekk.validate()
                }
            }
        }
        Given("Utleggstrekk med ugyldige feltverdier") {
            val ugyldige =
                listOf(
                    utleggstrekk.copy(navTrekkId = "123\t34") to "navTrekkId har ugyldige tegn",
                    utleggstrekk.copy(kreditorIdTss = "abc\n") to "kreditorIdTss er ugyldig",
                    utleggstrekk.copy(kreditorTrekkId = "fjd\t") to "kreditorTrekkId er ugyldig",
                    utleggstrekk.copy(debitorId = "fdsafsd\t") to "debitorId er ugyldig",
                    utleggstrekk.copy(kodeTrekktype = "234237943") to "kodeTrekkType er ugyldig",
                    utleggstrekk.copy(kid = "fdsa\t") to "kid er ugyldig",
                    utleggstrekk.copy(kilde = "fsdfdjsaiofdsajfsfs") to "kilde er ugyldig",
                    utleggstrekk.copy(saldo = -2.3) to "saldo < 0",
                    utleggstrekk.copy(prioritetFomDato = "fdsaifuh") to "prioritetFomDato er ugyldig",
                    utleggstrekk.copy(gyldigTomDato = "fdsaifuh") to "gyldigTomDato er ugyldig",
                    utleggstrekk.copy(perioder = Perioder(listOf(Periode("test", null, sats = 2000.0)))) to "periodeFomDato er ugyldig",
                    utleggstrekk.copy(perioder = Perioder(listOf(Periode("2002-11-10", null, sats = -1.0)))) to "sats er ugyldig",
                )
            Then("validate kaster exception") {
                ugyldige.forEachIndexed { i, (ugyldigTrekk, expected) ->
                    withClue("TREKK nr $i, $ugyldigTrekk skal ikke validere ") {
                        val exception = shouldThrow<Exception> { ugyldigTrekk.validate() }
                        exception.message shouldBe expected
                    }
                }
            }
        }

        afterSpec {
            clearAllMocks()
            unmockkObject(PropertiesConfig)
        }
    })