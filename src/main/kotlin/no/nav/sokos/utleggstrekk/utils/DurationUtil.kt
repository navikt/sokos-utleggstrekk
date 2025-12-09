package no.nav.sokos.utleggstrekk.utils

object DurationUtil {
    fun durationOf(block: () -> Unit): Long {
        val start = System.currentTimeMillis()
        block()
        return System.currentTimeMillis() - start
    }
}