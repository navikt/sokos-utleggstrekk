package no.nav.sokos.utleggstrekk.database

import no.nav.sokos.utleggstrekk.database.RepositoryExtensions.Parameter
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException

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

    fun PreparedStatement.withParameters(vararg parameters: Parameter?) = apply {
        var index = 1; parameters.forEach { it?.addToPreparedStatement(this, index++) }
    }

    private fun <T> ResultSet.toList(mapper: ResultSet.() -> T) = mutableListOf<T>().apply {
        while (next()) {
            add(mapper())
        }
    }

}