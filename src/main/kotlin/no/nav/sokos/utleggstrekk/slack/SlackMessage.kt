package no.nav.sokos.utleggstrekk.slack

import java.time.LocalDate

import kotlinx.serialization.Serializable

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

fun createSlackMessage(feilHeader: String, content: List<String>) =
    Data(
        text = ":package: $feilHeader",
        blocks = buildSections(feilHeader, content),
    )

private fun buildSections(feilHeader: String, content: List<String>): MutableList<Block> {
    val dividerBlock = Block(type = "divider")
    val headerBlock =
        Block(
            type = "header",
            text =
                Text(
                    type = "plain_text",
                    text = ":error:  $feilHeader  ",
                    emoji = true,
                ),
        )
    val overordnetInfoBlock =
        Block(
            type = "section",
            fields =
                listOf(
                    Field(
                        text = "*FeilMelding* error",
                    ),
                    Field(
                        text = "*Dato* \n${LocalDate.now()}",
                    ),
                ),
        )

    val feilmeldinger =
        content.map { utlegg ->
            Block(
                type = "section",
                fields =
                    listOf(
                        Field(text = "*Feilmelding*\n$utlegg"),
                        Field(text = "*Info* error"),
                    ),
            )
        }

    val blocks = mutableListOf<Block>()
    blocks.add(headerBlock)
    blocks.add(dividerBlock)
    blocks.add(overordnetInfoBlock)
    blocks.add(dividerBlock)
    blocks.addAll(feilmeldinger)
    blocks.add(dividerBlock)
    blocks.add(dividerBlock)
    return blocks
}
