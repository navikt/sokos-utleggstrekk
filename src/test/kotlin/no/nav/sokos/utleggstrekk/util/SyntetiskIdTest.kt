package no.nav.sokos.utleggstrekk.util

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe

import no.nav.sokos.utleggstrekk.domene.nav.TrekkAlternativ
import no.nav.sokos.utleggstrekk.utils.SyntetiskId

class SyntetiskIdTest :
    BehaviorSpec({
        Given("En id som er en UUID v4") {
            val trekkId = "550e8400-e29b-41d4-a716-446655440000"
            val result = SyntetiskId.konverterTrekkId(trekkId, TrekkAlternativ.LOPM)
            When("Trekkealternativ er LOPM") {
                Then("Blir id'en forkortet og får suffix M") {
                    result shouldBe "550e8400e29b41d4a716446655440000M"
                }
            }
            When("Trekkealternativ er LOPP") {
                val result = SyntetiskId.konverterTrekkId(trekkId, TrekkAlternativ.LOPP)
                Then("Blir id'en forkortet og får suffix P") {
                    result shouldBe "550e8400e29b41d4a716446655440000P"
                }
            }
        }
        Given("En id som er 34 tegn eller kortere") {
            val trekkId = "1234567"
            When("Trekkealternativ er LOPM") {
                val result = SyntetiskId.konverterTrekkId(trekkId, TrekkAlternativ.LOPM)
                Then("Blir id'en uendret og får suffix M") {
                    result shouldBe "1234567M"
                }
            }
            When("Trekkealternativ er LOPP") {
                val result = SyntetiskId.konverterTrekkId(trekkId, TrekkAlternativ.LOPP)
                Then("Blir id'en uendret og får suffix P") {
                    result shouldBe "1234567P"
                }
            }
        }
        Given("En id som er lenger enn 34 tegn og ikke UUID") {
            val trekkId = "550e8400ae29bc41d4fa716a446655440000"
            When("Trekkealternativ er LOPM") {
                val result = SyntetiskId.konverterTrekkId(trekkId, TrekkAlternativ.LOPM)
                Then("Blir id'en en BASE64 encodet digest og får suffix M") {
                    result shouldBe "1GfhInHamdFvDzH4Yowa5-rgOQNOGykk-M"
                    result.length shouldBeLessThanOrEqual 35
                }
            }
            When("Trekkealternativ er LOPP") {
                val result = SyntetiskId.konverterTrekkId(trekkId, TrekkAlternativ.LOPP)
                Then("Blir id'en BASE64 encodet digest og får suffix P") {
                    result shouldBe "1GfhInHamdFvDzH4Yowa5-rgOQNOGykk-P"
                    result.length shouldBeLessThanOrEqual 35
                }
            }
        }
    })