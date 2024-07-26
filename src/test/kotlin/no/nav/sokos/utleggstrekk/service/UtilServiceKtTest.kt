package no.nav.sokos.utleggstrekk.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class UtilServiceKtTest : FunSpec({

    test("Når Januar er startmåned skal 31.Desember året før være sluttperioden") {
        "2023-01".previousPeriod() shouldBe "2022-12-31"
    }

    test("Når Februar er startmåned skal 31.Januar være sluttperioden") {
        "2023-02".previousPeriod() shouldBe "2023-01-31"
    }

    test("Når Mars er startmåned skal 28.februar være sluttperioden") {
        "2023-03".previousPeriod() shouldBe "2023-02-28"
    }

    test("Når April er startmåned skal 31.Mars være sluttperioden") {
        "2023-04".previousPeriod() shouldBe "2023-03-31"
    }

    test("Når Mai er startmåned skal 30.April være sluttperioden") {
        "2023-05".previousPeriod() shouldBe "2023-04-30"
    }

    test("Når Juni er startmåned skal 31.Mai være sluttperioden") {
        "2023-06".previousPeriod() shouldBe "2023-05-31"
    }

    test("Når Juli er startmåned skal 30.Juni være sluttperioden") {
        "2023-07".previousPeriod() shouldBe "2023-06-30"
    }

    test("Når August er startmåned skal 31.Juli være sluttperioden") {
        "2023-08".previousPeriod() shouldBe "2023-07-31"
    }

    test("Når September er startmåned skal 31.August være sluttperioden") {
        "2023-09".previousPeriod() shouldBe "2023-08-31"
    }

    test("Når Oktober er startmåned skal 30.September være sluttperioden") {
        "2023-10".previousPeriod() shouldBe "2023-09-30"
    }

    test("Når November er startmåned skal 31.Oktober være sluttperioden") {
        "2023-11".previousPeriod() shouldBe "2023-10-31"
    }

    test("Når Desember er startmåned skal 30.November være sluttperioden") {
        "2023-12".previousPeriod() shouldBe "2023-11-30"
    }
})
