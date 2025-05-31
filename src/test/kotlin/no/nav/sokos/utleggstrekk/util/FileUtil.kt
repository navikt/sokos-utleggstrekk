package no.nav.sokos.utleggstrekk.util

import java.io.Reader

object FileUtil {
    fun fileAsString(fileName: String): String = fileAs(fileName, Reader::readText)

    fun fileAsList(fileName: String): List<String> = fileAs(fileName, Reader::readLines)

    private fun <T> fileAs(
        fileName: String,
        func: Reader.() -> T,
    ): T =
        this::class.java
            .getResourceAsStream(fileName)!!
            .bufferedReader()
            .use { it.func() }
}
