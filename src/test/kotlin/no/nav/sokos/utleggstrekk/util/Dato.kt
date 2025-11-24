package no.nav.sokos.utleggstrekk.util

import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter

private val formatter = DateTimeFormatter.ISO_DATE

val String.asDate: LocalDate get() = LocalDate.parse(this, formatter)

infix fun String.etter(p: Period): String = asDate.plus(p).format(formatter)

infix fun String.tidligere(p: Period): String = asDate.minus(p).format(formatter)

val Int.dager: Period get() = Period.ofDays(this)
val Int.mnd: Period get() = Period.ofMonths(this)

val idag: String get() = LocalDate.now().format(formatter)