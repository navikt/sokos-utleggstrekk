package no.nav.sokos.utleggstrekk.util

import java.io.File
import java.net.URL

object TestUtils {
    private fun String.asResource(): URL = this::class.java.getResource(this)!!

    fun resourceToString(filename: String) = filename.asResource().readText()

    fun resourceToStringList(filename: String) = resourceToString(filename).lines()

    fun fileAsString(fileName: String): String =
        this::class.java
            .getResourceAsStream("${File.separator}$fileName")!!
            .bufferedReader()
            .use { it.readText() }
}
