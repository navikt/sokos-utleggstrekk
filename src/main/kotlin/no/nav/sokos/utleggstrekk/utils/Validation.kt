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

    fun String.isValidKid(): Boolean {
        if (length < 2 || length > 25) return false
        if (!all { it.isDigit() }) return false
        return isValidMod10() || isValidMod11()
    }

    private fun String.isValidMod10(): Boolean {
        val digits = map { it.digitToInt() }
        var sum = 0
        var double = true
        for (i in digits.size - 2 downTo 0) {
            var d = digits[i]
            if (double) {
                d *= 2
                if (d > 9) d -= 9
            }
            sum += d
            double = !double
        }
        val checkDigit = (10 - (sum % 10)) % 10
        return checkDigit == digits.last()
    }

    private fun String.isValidMod11(): Boolean {
        val weights = intArrayOf(2, 3, 4, 5, 6, 7)
        val digits = map { it.digitToInt() }
        var sum = 0
        for (i in digits.size - 2 downTo 0) {
            val weightIndex = (digits.size - 2 - i) % weights.size
            sum += digits[i] * weights[weightIndex]
        }
        val remainder = sum % 11
        val checkDigit =
            when (remainder) {
                0 -> 0
                1 -> return false // MOD11 with remainder 1 is invalid (no valid check digit)
                else -> 11 - remainder
            }
        return checkDigit == digits.last()
    }
}
