package no.nav.sokos.utleggstrekk.utils

import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException

object Validation {
    fun String.isNumber(): Boolean = this.isNotEmpty() && this.all { it in '0'..'9' }

    fun String.inRange(min: Int, max: Int): Boolean = this.length >= min && this.length <= max

    // Catches DateTimeParseException specifically rather than the broad Exception to avoid masking unexpected errors.
    fun String.isDate() =
        try {
            LocalDate.parse(this)
            true
        } catch (e: DateTimeParseException) {
            false
        }

    // Catches DateTimeParseException specifically rather than the broad Exception to avoid masking unexpected errors.
    fun String.isDateTime() =
        try {
            Instant.parse(this)
            true
        } catch (e: DateTimeParseException) {
            false
        }

    fun String.isSafeText(allowNewlines: Boolean = false): Boolean =
        this.none { ch ->
            when {
                ch == '\uFFFD' -> true
                ch.code in 0x00..0x1F -> !(allowNewlines && (ch == '\n' || ch == '\r' || ch == '\t'))
                ch.code == 0x7F -> true
                ch.code in 0x90..0x9F -> true
                Character.getType(ch) == Character.FORMAT.toInt() -> true
                else -> false
            }
        }

    fun String.validateString(allowNewLines: Boolean = false) {
        if (!isSafeText(allowNewLines)) throw IllegalArgumentException("Malformed string data")
    }
}
