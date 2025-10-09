package no.nav.sokos.utleggstrekk.domene.nav

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch

import no.nav.sokos.utleggstrekk.service.ErrorMessage

class SlackMessageTest :
    FunSpec({
        test("Skal returnere en slack melding") {
            val header = "Feil header"
            val messages =
                listOf(
                    ErrorMessage("Feil 1", mutableListOf("Feil info 1")),
                    ErrorMessage("Feil 2", mutableListOf("Feil info 2", "Feil info 3")),
                )

            val slackMessage = createSlackMessage(header, messages)

            slackMessage.text shouldBe ":package: $header"
            slackMessage.blocks shouldHaveSize 9

            val headerBlock = slackMessage.blocks[0]
            headerBlock.type shouldBe "header"
            headerBlock.text?.text shouldBe ":error:  $header  "

            val datoBlock = slackMessage.blocks[2]
            datoBlock.type shouldBe "section"
            datoBlock.fields?.shouldHaveSize(1)
            datoBlock.fields?.last()?.text shouldMatch "\\*Dato\\* \n\\d{4}-\\d{2}-\\d{2}".toRegex()

            val errorMessageBlock1 = slackMessage.blocks[4]
            errorMessageBlock1.type shouldBe "section"
            errorMessageBlock1.fields?.shouldHaveSize(2)
            errorMessageBlock1.fields?.first()?.text shouldBe "*Feilmelding*\nFeil 1"
            errorMessageBlock1.fields?.last()?.text shouldBe "*Info*\nFeil info 1"

            val errorMessageBlock2 = slackMessage.blocks[5]
            errorMessageBlock2.type shouldBe "section"
            errorMessageBlock2.fields?.shouldHaveSize(2)
            errorMessageBlock2.fields?.first()?.text shouldBe "*Feilmelding*\nFeil 2"
            errorMessageBlock2.fields?.last()?.text shouldBe "*Info*\nFeil info 2"

            val errorMessageBlock3 = slackMessage.blocks[6]
            errorMessageBlock3.type shouldBe "section"
            errorMessageBlock3.fields?.shouldHaveSize(2)
            errorMessageBlock3.fields?.first()?.text shouldBe "*Feilmelding*\nFeil 2"
            errorMessageBlock3.fields?.last()?.text shouldBe "*Info*\nFeil info 3"

            val dividers = slackMessage.blocks.filter { it.type == "divider" }
            dividers shouldHaveSize 4
        }
    })