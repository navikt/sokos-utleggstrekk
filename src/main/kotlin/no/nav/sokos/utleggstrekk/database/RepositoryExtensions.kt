package no.nav.sokos.utleggstrekk.database
import no.nav.sokos.utleggstrekk.database.RepositoryExtensions.Parameter
import no.nav.sokos.utleggstrekk.database.model.MidlertidigStansTable
import no.nav.sokos.utleggstrekk.database.model.TrekkTable
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

    fun param(value: Int) =
        Parameter { statement: PreparedStatement, index: Int -> statement.setInt(index, value) }

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

    fun ResultSet.toTrekkTable() =
        toList {
            TrekkTable(
                trekktableid = getColumn("id"),
                sekvensnr = getColumn("sekvensnr"),
                trekkid = getColumn("trekkid_ske"),
                trekkversjon = getColumn("trekkversjon"),
                trekkopprettet = getColumn("trekkopprettet"),
                trekkpliktig = getColumn("trekkpliktig"),
                skyldner = getColumn("skyldner"),
                trekkstatus = getColumn("trekkstatus"),
                startPeriode = getColumn("startperiode"),
                sluttPeriode = getColumn("sluttperiode"),
                trekkbelop = getColumn("trekkbelop"),
                trekkprosent = getColumn("trekkprosent"),
                kid = getColumn("kid"),
                kontonummer = getColumn("kontonummer"),
                corrid = getColumn("corrid"),
                status = getColumn("status"),
                tidspunktMottatt = getColumn("tidspunkt_mottatt"),
                tidspunktSendtOs = getColumn("tidspunkt_sendt_os"),
                tidspunktSisteStatus = getColumn("tidspunkt_siste_status"),
                tidspunktOpprettet = getColumn("tidspunkt_opprettet"),
            )
        }

    fun ResultSet.toMidlertidigStans() =
        toList {
            MidlertidigStansTable(
                midlertidigstansid = getColumn("id"),
                trekksekvensnr = getColumn("trekksekvensnr"),
                startPeriode = getColumn("startperiode"),
                sluttPeriode = getColumn("sluttperiode"),
            )
        }
}