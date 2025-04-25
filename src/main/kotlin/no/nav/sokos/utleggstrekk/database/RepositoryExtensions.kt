package no.nav.sokos.utleggstrekk.database
import io.ktor.utils.io.InternalAPI
import no.nav.sokos.utleggstrekk.database.RepositoryExtensions.Parameter
import no.nav.sokos.utleggstrekk.database.model.TrekkPeriodeTable
import no.nav.sokos.utleggstrekk.database.model.UtleggstrekkTable
import java.math.BigDecimal
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime

object RepositoryExtensions {
    inline fun <R> Connection.useAndHandleErrors(block: (Connection) -> R): R {
        try {
            use {
                return block(this).also { close() }
            }
        } catch (ex: SQLException) {
            println("Feil ved kall til database: ${ex.errorCode}, ${ex.message}.")
            throw ex
        }
    }

    fun interface Parameter {
        fun addToPreparedStatement(sp: PreparedStatement, index: Int)
    }

    fun param(value: Int) =
        Parameter { statement: PreparedStatement, index: Int -> statement.setInt(index, value) }

    fun param(value: Long) =
        Parameter { statement: PreparedStatement, index: Int -> statement.setLong(index, value) }

    fun param(value: String) =
        Parameter { statement: PreparedStatement, index: Int -> statement.setString(index, value) }

    fun param(value: LocalDateTime) =
        Parameter { statement: PreparedStatement, index: Int -> statement.setTimestamp(index, Timestamp.valueOf(value)) }

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

    fun ResultSet.toUtleggstrekkTable() =
        toList {
            UtleggstrekkTable(
                utleggstrekkTableId = getColumn("id"),
                sekvensnummer = getColumn("sekvensnummer"),
                saksnummer = getColumn("saksnummer"),
                trekkidSke = getColumn("trekkid_ske"),
                trekkversjon = getColumn("trekkversjon"),
                opprettetSke = getColumn("opprettet_ske"),
                trekkpliktig = getColumn("trekkpliktig"),
                skyldner = getColumn("skyldner"),
                trekkstatus = getColumn("trekkstatus"),
                kid = getColumn("kid"),
                kontonummer = getColumn("kontonummer"),
                betalingsmottaker = getColumn("betalingsmottaker"),
                corrid = getColumn("corrid"),
                status = getColumn("status"),
                tidspunktSendtOs = getColumn("tidspunkt_sendt_os"),
                tidspunktSisteStatus = getColumn("tidspunkt_siste_status"),
                tidspunktOpprettet = getColumn("tidspunkt_opprettet"),
            )
        }

    fun ResultSet.toTrekkPeriodeTable() =
        toList {
            TrekkPeriodeTable(
                trekkPeriodeTableId = getColumn("id"),
                sekvensnummer = getColumn("sekvensnummer"),
                trekkidSke = getColumn("trekkid_ske"),
                trekkversjon = getColumn("trekkversjon"),
                datoStart = getColumn("dato_start"),
                datoSlutt = getColumn("dato_slutt"),
                sats = getColumn("sats"),
                trekkAlternativ = getColumn("trekkalternativ"),
            )
        }

}