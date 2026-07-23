package no.nav.sokos.utleggstrekk.domene.nav

import java.time.LocalDate

import kotlinx.serialization.Serializable

import no.nav.sokos.utleggstrekk.service.ErrorMessage

@Serializable
data class Data(
    val text: String,
    val blocks: List<Block>,
)

@Serializable
data class Block(
    val type: String,
    val text: Text? = null,
    val fields: List<Field>? = null,
)

@Serializable
data class Text(
    val type: String = "plain_text",
    val text: String,
    val emoji: Boolean = false,
)

@Serializable
data class Field(
    val type: String = "mrkdwn",
    val text: String,
)

fun createSlackMessage(messageTitle: ErrorCategory, content: List<ErrorMessage>) =
    Data(
        text = ":package: $messageTitle",
        blocks = buildSections(messageTitle, content),
    )

private fun buildSections(messageTitle: ErrorCategory, content: List<ErrorMessage>): MutableList<Block> {
    val dividerBlock = Block(type = "divider")
    val headerBlock =
        Block(
            type = "header",
            text =
                Text(
                    type = "plain_text",
                    text = ":error:  $messageTitle",
                    emoji = true,
                ),
        )
    val datoBlock =
        Block(
            type = "section",
            fields =
                listOf(
                    Field(
                        text = "*Dato* \n${LocalDate.now()}",
                    ),
                ),
        )

    val errorMessages =
        content
            .map { (errorType, info) ->
                info.map { (description, referenceId) ->
                    Block(
                        type = "section",
                        fields =
                            listOf(
                                Field(text = "*Feilmelding*\n$errorType"),
                                Field(text = "*Info*\n$description\n*Korrelasjons- TransaksjonsID* $referenceId"),
                            ),
                    )
                }
            }.flatten()

    val blocks = mutableListOf<Block>()
    blocks.add(headerBlock)
    blocks.add(dividerBlock)
    blocks.add(datoBlock)
    blocks.add(dividerBlock)
    blocks.addAll(errorMessages)
    blocks.add(dividerBlock)
    blocks.add(dividerBlock)
    return blocks
}
