package no.nav.sokos.utleggstrekk.database

import no.nav.sokos.utleggstrekk.database.RepositoryExtensions.Parameter
import java.math.BigDecimal
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.time.LocalDate
import java.time.LocalDateTime

object RepositoryExtensions {
    inline fun <R> Connection.useAndHandleErrors(block: (Connection) -> R): R {
        try {
            use {
                return block(this)
            }
        } catch (ex: SQLException) {
            throw ex
        }
    }

    fun interface Parameter {
        fun addToPreparedStatement(sp: PreparedStatement, index: Int)
    }

    fun param(value: String?) = Parameter { sp: PreparedStatement, index: Int -> sp.setString(index, value) }

    fun PreparedStatement.withParameters(vararg parameters: Parameter?) =
        apply {
            var index = 1
            parameters.forEach { it?.addToPreparedStatement(this, index++) }
        }

    private fun <T> ResultSet.toList(mapper: ResultSet.() -> T) =
        mutableListOf<T>().apply {
            while (next()) {
                add(mapper())
            }
        }

    inline fun <reified T : Any?> ResultSet.getColumn(
        columnLabel: String,
        transform: (T) -> T = { it },
    ): T {
        val columnValue =
            when (T::class) {
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
}