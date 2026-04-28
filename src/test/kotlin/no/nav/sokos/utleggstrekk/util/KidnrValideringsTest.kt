package no.nav.sokos.utleggstrekk.util

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

import no.nav.sokos.utleggstrekk.utils.Validation.isValidKid

class KidnrValideringsTest :
    FunSpec({

        context("MOD10 (Luhn) validering") {
            test("gyldig KID med MOD10 kontrollsiffer") {
                "2345676".isValidKid() shouldBe true
            }

            test("ugyldig KID med feil MOD10 kontrollsiffer") {
                "2345675".isValidKid() shouldBe false
            }
        }

        context("MOD11 validering") {
            test("gyldig KID med MOD11 kontrollsiffer") {
                "12345678903".isValidKid() shouldBe true
            }

            test("ugyldig KID med feil MOD11 kontrollsiffer") {
                "12345678901".isValidKid() shouldBe false
            }
        }

        context("Generell validering") {
            test("for kort KID (mindre enn 2 siffer) er ugyldig") {
                "1".isValidKid() shouldBe false
            }

            test("for lang KID (mer enn 25 siffer) er ugyldig") {
                "12345678901234567890123456".isValidKid() shouldBe false
            }

            test("KID med bokstaver er ugyldig") {
                "1234AB".isValidKid() shouldBe false
            }

            test("tom streng er ugyldig") {
                "".isValidKid() shouldBe false
            }

            test("KID med spesialtegn er ugyldig") {
                "1234-56".isValidKid() shouldBe false
            }

            test("Test av alle checksum tegn") {
                val mod11 =
                    listOf<String>(
                        "9784083060",
                        "8602499031",
                        "2219356482",
                        "1604892763",
                        "2191507714",
                        "3794978025",
                        "0528006426",
                        "3905764917",
                        "3383245388",
                        "8449051239",
                    )
                val mod10 =
                    listOf<String>(
                        "7484530790",
                        "4535061081",
                        "2461594232",
                        "6488103323",
                        "7013092254",
                        "2040816585",
                        "4438387906",
                        "4423381997",
                        "3221507688",
                        "5115486739",
                    )
                mod10.forEach {
                    withClue("$it ikke gyldig") {
                        it.isValidKid() shouldBe true
                    }
                }
                mod11.forEach {
                    withClue("$it ikke gyldig") {
                        it.isValidKid() shouldBe true
                    }
                }
            }

            test("KID med 2 siffer og gyldig MOD10 er gyldig") {
                "18".isValidKid() shouldBe true
            }

            test("KID med 25 siffer og gyldig kontrollsiffer er gyldig") {
                // 24 digits + MOD10 check digit
                "1234567890123456789012340".isValidKid() shouldBe true
            }

            test("ugyldig KID når MOD11 gir remainder lik 1 og ingen gyldig kontrollsiffer") {
                "66".isValidKid() shouldBe false
            }
        }
    })