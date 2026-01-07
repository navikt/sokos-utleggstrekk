package no.nav.sokos.utleggstrekk.utils

import java.time.Instant
import java.time.LocalDate

object Validation {
    val UUID_MATCHER = Regex("^[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}$")

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

    fun String.isUuidV4(): Boolean {
        if (length != 36) return false
        return UUID_MATCHER.matches(this)
    }
}
