package no.nav.sokos.utleggstrekk.utils

import java.time.Instant
import java.time.LocalDate

object Validation {
    fun String.isNumber(): Boolean = this.isNotEmpty() && this.all { it in '0'..'9' }

    fun String.inRange(min: Int, max: Int): Boolean = this.length >= min && this.length <= max

    fun String.isDate() =
        try {
            LocalDate.parse(this)
            true
        } catch (e: Exception) {
            false
        }

    fun String.isDateTime() =
        try {
            Instant.parse(this)
            true
        } catch (e: Exception) {
            false
        }

    fun String.isSafeText() =
        this.none { ch ->
            val c = ch.code
            ch == '\uFFFD' || (c in 0x00..0x1F) || c == 0x7f || c in 0x80..0x9f || Character.getType(ch) == Character.FORMAT.toInt()
        }
}
