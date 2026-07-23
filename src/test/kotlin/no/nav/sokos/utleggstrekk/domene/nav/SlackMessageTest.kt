package no.nav.sokos.utleggstrekk.domene.nav

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch

import no.nav.sokos.utleggstrekk.service.ErrorInfo
import no.nav.sokos.utleggstrekk.service.ErrorMessage

class SlackMessageTest :
    FunSpec({
        test("Skal returnere en slack melding") {
            val messages =
                listOf(
                    ErrorMessage(ErrorHeader.FEIL_FRA_SKE, "Feil info 1"),
                    ErrorMessage(ErrorHeader.FEIL_VED_SENDING, mutableListOf(ErrorInfo("Feil info 2", "KorrelasjonId"), ErrorInfo("Feil info 3"))),
                )

            val slackMessage = createSlackMessage(ErrorCategory.TREKK_HENTING, messages)

            slackMessage.text shouldBe ":package: ${ErrorCategory.TREKK_HENTING}"
            slackMessage.blocks shouldHaveSize 9

            val headerBlock = slackMessage.blocks[0]
            headerBlock.type shouldBe "header"
            headerBlock.text?.text shouldBe ":error:  ${ErrorCategory.TREKK_HENTING}"

            val datoBlock = slackMessage.blocks[2]
            datoBlock.type shouldBe "section"
            datoBlock.fields?.shouldHaveSize(1)
            datoBlock.fields?.last()?.text shouldMatch "\\*Dato\\* \n\\d{4}-\\d{2}-\\d{2}".toRegex()

            val errorMessageBlock1 = slackMessage.blocks[4]
            errorMessageBlock1.type shouldBe "section"
            errorMessageBlock1.fields?.shouldHaveSize(2)
            errorMessageBlock1.fields?.first()?.text shouldBe "*Feilmelding*\n${ErrorHeader.FEIL_FRA_SKE}"
            errorMessageBlock1.fields?.last()?.text shouldBe "*Info*\nFeil info 1\n*Korrelasjons- og TransaksjonsID* Ingen"

            val errorMessageBlock2 = slackMessage.blocks[5]
            errorMessageBlock2.type shouldBe "section"
            errorMessageBlock2.fields?.shouldHaveSize(2)
            errorMessageBlock2.fields?.first()?.text shouldBe "*Feilmelding*\n${ErrorHeader.FEIL_VED_SENDING}"
            errorMessageBlock2.fields?.last()?.text shouldBe "*Info*\nFeil info 2\n*Korrelasjons- og TransaksjonsID* KorrelasjonId"

            val errorMessageBlock3 = slackMessage.blocks[6]
            errorMessageBlock3.type shouldBe "section"
            errorMessageBlock3.fields?.shouldHaveSize(2)
            errorMessageBlock3.fields?.first()?.text shouldBe "*Feilmelding*\n${ErrorHeader.FEIL_VED_SENDING}"
            errorMessageBlock3.fields?.last()?.text shouldBe "*Info*\nFeil info 3\n*Korrelasjons- og TransaksjonsID* Ingen"

            val dividers = slackMessage.blocks.filter { it.type == "divider" }
            dividers shouldHaveSize 4
        }
    })
