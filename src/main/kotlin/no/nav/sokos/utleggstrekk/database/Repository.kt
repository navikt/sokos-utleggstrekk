package sokos.utleggstrekk.database

import mu.KotlinLogging
import java.math.BigDecimal
import java.sql.ResultSet
import java.sql.SQLException
import java.time.LocalDate
import java.time.LocalDateTime

private val logger = KotlinLogging.logger {  }
object Repository {}

inline fun <reified T : Any?> ResultSet.getColumn(
    columnLabel: String,
    transform: (T) -> T = { it },
): T {
    val columnValue = when (T::class) {
        Int::class -> getInt(columnLabel)
        Long::class -> getLong(columnLabel)
        Char::class -> getString(columnLabel)?.get(0) ?: ' '
        Double::class -> getDouble(columnLabel)
        String::class -> getString(columnLabel)?.trim() ?: ""
        Boolean::class -> getBoolean(columnLabel)
        BigDecimal::class -> getBigDecimal(columnLabel)
        LocalDate::class -> getDate(columnLabel)?.toLocalDate()
        LocalDateTime::class -> getTimestamp(columnLabel)?.toLocalDateTime()

        else -> {
            println("Kunne ikke mappe fra resultatsett til datafelt av type ${T::class.simpleName}")
            throw SQLException("Kunne ikke mappe fra resultatsett til datafelt av type ${T::class.simpleName}")
        }
    }

    if (null !is T && columnValue == null) {
        println("Påkrevet kolonne '$columnLabel' er null")
        throw SQLException("Påkrevet kolonne '$columnLabel' er null")
    }

    return transform(columnValue as T)
}
