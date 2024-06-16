package nav.no.sokos.utleggstrekk.service

fun getResourceAsText(file: String): String? =
    object {}.javaClass.getResource(file)?.readText()