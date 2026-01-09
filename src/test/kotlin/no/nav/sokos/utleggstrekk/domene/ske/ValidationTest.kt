package no.nav.sokos.utleggstrekk.domene.ske

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.BehaviorSpec

import no.nav.sokos.utleggstrekk.AppSettings
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

        val utleggstrekk =
            InnrapporteringTrekk(
                aksjonskode = Aksjonskode.NY,
                navTrekkId = "1234",
                kreditorIdTss = AppSettings.skeConfig.skeTSSId,
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
                    trekkpaalegg.copy(trekkid = "1232\t"),
                    trekkpaalegg.copy(sekvensnummer = -1),
                    trekkpaalegg.copy(trekkversjon = -1),
                    trekkpaalegg.copy(opprettet = "2026-01-01"),
                    trekkpaalegg.copy(saksnummer = "sak\n"),
                    trekkpaalegg.copy(trekkpliktig = "trekkpliktig"),
                    trekkpaalegg.copy(skyldner = "bob"),
                    trekkpaalegg.copy(trekkstoerrelseForPeriode = listOf(trekkpaalegg.trekkstoerrelseForPeriode.first().copy(startdato = "342"))),
                    trekkpaalegg.copy(trekkstoerrelseForPeriode = listOf(trekkpaalegg.trekkstoerrelseForPeriode.first().copy(sluttdato = "342"))),
                    trekkpaalegg.copy(trekkstoerrelseForPeriode = listOf(trekkpaalegg.trekkstoerrelseForPeriode.first().copy(trekkbeloep = Trekkbeloep(-1000.0)))),
                    trekkpaalegg.copy(trekkstoerrelseForPeriode = listOf(trekkpaalegg.trekkstoerrelseForPeriode.first().copy(trekkbeloep = null, trekkprosent = Trekkprosent(110.0)))),
                    trekkpaalegg.copy(trekkstoerrelseForPeriode = listOf(trekkpaalegg.trekkstoerrelseForPeriode.first().copy(trekkbeloep = null, trekkprosent = Trekkprosent(-20.0)))),
                    trekkpaalegg.copy(betalingsinformasjon = trekkpaalegg.betalingsinformasjon.copy(betalingsmottaker = "1123131111221")),
                    trekkpaalegg.copy(betalingsinformasjon = trekkpaalegg.betalingsinformasjon.copy(kidnummer = "112hfd1")),
                    trekkpaalegg.copy(betalingsinformasjon = trekkpaalegg.betalingsinformasjon.copy(kontonummer = "konto")),
                )
            Then("Validate kaster exception") {
                ugyldige.forEachIndexed { i, ugyldigTrekk ->
                    withClue("TREKK nr. $i, $ugyldigTrekk skal ikke validere") {
                        shouldThrow<Exception> { ugyldigTrekk.validate() }
                    }
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
                    utleggstrekk.copy(navTrekkId = "123\t34"),
                    utleggstrekk.copy(kreditorIdTss = "abc\n"),
                    utleggstrekk.copy(kreditorTrekkId = "fjd\t"),
                    utleggstrekk.copy(debitorId = "fdsafsd\t"),
                    utleggstrekk.copy(kodeTrekktype = "234237943"),
                    utleggstrekk.copy(kid = "fdsa\t"),
                    utleggstrekk.copy(kilde = "fsdfdjsaiofdsajfsfs"),
                    utleggstrekk.copy(saldo = -2.3),
                    utleggstrekk.copy(prioritetFomDato = "fdsaifuh"),
                    utleggstrekk.copy(gyldigTomDato = "fdsaifuh"),
                    utleggstrekk.copy(perioder = Perioder(listOf(Periode("test", null, sats = 2000.0)))),
                    utleggstrekk.copy(perioder = Perioder(listOf(Periode("2002-11-10", null, sats = -1.0)))),
                )
            Then("validate kaster exception") {
                ugyldige.forEachIndexed { i, ugyldigTrekk ->
                    withClue("TREKK nr $i, $ugyldigTrekk skal ikke validere ") {
                        shouldThrow<Exception> { ugyldigTrekk.validate() }
                    }
                }
            }
        }
    })