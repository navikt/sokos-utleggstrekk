package no.nav.sokos.utleggstrekk.util

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

            test("KID med 2 siffer og gyldig MOD10 er gyldig") {
                "18".isValidKid() shouldBe true
            }

            test("KID med 25 siffer og gyldig kontrollsiffer er gyldig") {
                // 24 digits + MOD10 check digit
                "1234567890123456789012340".isValidKid() shouldBe true
            }
        }
    })