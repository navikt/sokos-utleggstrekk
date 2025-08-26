package no.nav.sokos.utleggstrekk.database

import io.ktor.utils.io.InternalAPI
import kotlinx.datetime.toKotlinLocalDateTime
import mu.KotlinLogging
import java.math.BigDecimal
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.time.LocalDate
import java.time.LocalDateTime

object RepositoryExtensions {

    val logger = KotlinLogging.logger { }

    inline fun <R> Connection.useAndHandleErrors(block: (Connection) -> R): R {
        try {
            use {
                return block(this).also { close() }
            }
        } catch (ex: SQLException) {
            logger.error("Feil ved kall til database: ${ex.errorCode}, ${ex.message}.")
            throw ex
        }
    }

    fun interface Parameter {
        fun addToPreparedStatement(sp: PreparedStatement, index: Int)
    }

    fun param(value: Int) =
        Parameter { statement: PreparedStatement, index: Int -> statement.setInt(index, value) }

    fun param(value: String) =
        Parameter { statement: PreparedStatement, index: Int -> statement.setString(index, value) }

    fun PreparedStatement.withParameters(vararg parameters: Parameter?) =
        apply {
            parameters.forEachIndexed { index, param -> param?.addToPreparedStatement(this, index + 1) }
        }

    private fun <T> ResultSet.toList(mapper: ResultSet.() -> T) =
        mutableListOf<T>().apply {
            while (next()) {
                add(mapper())
            }
        }

    @OptIn(InternalAPI::class)
    inline fun <reified T : Any?> ResultSet.getColumn(
        columnLabel: String,
        transform: (T) -> T = { it },
    ): T {
        val columnValue =
            when (T::class) {
                Int::class -> getInt(columnLabel)
                Long::class -> getLong(columnLabel)
                Char::class -> getString(columnLabel)?.get(0) ?: ' '
                Double::class -> getString(columnLabel)?.toDouble()
                String::class -> getString(columnLabel)?.trim() ?: ""
                Boolean::class -> getBoolean(columnLabel)
                BigDecimal::class -> getBigDecimal(columnLabel)
                LocalDate::class -> getDate(columnLabel)?.toLocalDate()
                LocalDateTime::class -> getTimestamp(columnLabel)?.toLocalDateTime()
                kotlinx.datetime.LocalDateTime::class -> getTimestamp(columnLabel)?.toLocalDateTime()?.toKotlinLocalDateTime()
                else -> {
                    logger.error("Kunne ikke mappe fra resultatsett til datafelt av type ${T::class.simpleName}")
                    throw SQLException("Kunne ikke mappe fra resultatsett til datafelt av type ${T::class.simpleName}")
                }
            }

        if (null !is T && columnValue == null) {
            logger.info("Påkrevet kolonne '$columnLabel' er null")
            throw SQLException("Påkrevet kolonne '$columnLabel' er null")
        }

        return transform(columnValue as T)
    }
}