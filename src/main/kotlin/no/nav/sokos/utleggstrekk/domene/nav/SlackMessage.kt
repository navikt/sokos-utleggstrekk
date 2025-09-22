package no.nav.sokos.utleggstrekk.domene.nav

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

fun createSlackMessage(feilHeader: String, filnavn: String, content: Map<String, List<String>>) =
    Data(
        text = ":package: $feilHeader",
        blocks = buildSections(feilHeader, filnavn, content),
    )

private fun buildSections(feilHeader: String, filnavn: String, content: Map<String, List<String>>): MutableList<Block> {
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
    val filnavnBlock =
        Block(
            type = "section",
            fields =
                listOf(
                    Field(
                        text = "*Filnavn* \n$filnavn",
                    ),
                    Field(
                        text = "*Dato* \n${LocalDate.now()}",
                    ),
                ),
        )

    val feilmeldinger =
        content.map { entry ->
            entry.value.map { error ->
                Block(
                    type = "section",
                    fields =
                        listOf(
                            Field(text = "*Feilmelding*\n${entry.key}"),
                            Field(text = "*Info*\n$error"),
                        ),
                )
            }
        }

    val blocks = mutableListOf<Block>()
    blocks.add(headerBlock)
    blocks.add(dividerBlock)
    blocks.add(filnavnBlock)
    blocks.add(dividerBlock)
    feilmeldinger.forEach { blocks.addAll(it) }
    blocks.add(dividerBlock)
    blocks.add(dividerBlock)
    return blocks
}
