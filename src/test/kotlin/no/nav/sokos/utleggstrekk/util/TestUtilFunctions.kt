package no.nav.sokos.utleggstrekk.util

import java.io.Reader

object TestUtilFunctions {
    fun fileAsString(fileName: String): String = fileAs(fileName, Reader::readText)

    private fun <T> fileAs(fileName: String, func: Reader.() -> T): T =
        this::class.java
            .getResourceAsStream(fileName)!!
            .bufferedReader()
            .use { it.func() }
}