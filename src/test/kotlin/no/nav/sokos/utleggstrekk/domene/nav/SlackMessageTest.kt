package no.nav.sokos.utleggstrekk.domene.nav

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch

class SlackMessageTest :
    FunSpec({
        test("Skal returnere en ") {
            val header = "Feil header"
            val messages =
                mapOf(
                    "Feil 1" to listOf("Feil info 1"),
                    "Feil 2" to listOf("Feil info 2"),
                )

            val slackMessage = createSlackMessage(header, messages)

            slackMessage.text shouldBe ":package: $header"
            slackMessage.blocks shouldHaveSize 8

            val headerBlock = slackMessage.blocks[0]
            headerBlock.type shouldBe "header"
            headerBlock.text?.text shouldBe ":error:  $header  "

            val datoBlock = slackMessage.blocks[2]
            datoBlock.type shouldBe "section"
            datoBlock.fields?.shouldHaveSize(1)
            datoBlock.fields?.last()?.text shouldMatch "\\*Dato\\* \n\\d{4}-\\d{2}-\\d{2}".toRegex()

            val feilmeldingBlock1 = slackMessage.blocks[4]
            feilmeldingBlock1.type shouldBe "section"
            feilmeldingBlock1.fields?.shouldHaveSize(2)
            feilmeldingBlock1.fields?.first()?.text shouldBe "*Feilmelding*\nFeil 1"
            feilmeldingBlock1.fields?.last()?.text shouldBe "*Info*\nFeil info 1"

            val feilmeldingBlock2 = slackMessage.blocks[5]
            feilmeldingBlock2.type shouldBe "section"
            feilmeldingBlock2.fields?.shouldHaveSize(2)
            feilmeldingBlock2.fields?.first()?.text shouldBe "*Feilmelding*\nFeil 2"
            feilmeldingBlock2.fields?.last()?.text shouldBe "*Info*\nFeil info 2"

            val dividers = slackMessage.blocks.filter { it.type == "divider" }
            dividers shouldHaveSize 4
        }
    })