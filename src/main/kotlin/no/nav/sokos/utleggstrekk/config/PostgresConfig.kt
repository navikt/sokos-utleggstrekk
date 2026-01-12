package no.nav.sokos.utleggstrekk.config

import kotlinx.serialization.Serializable

@Serializable
data class PostgresConfig(
    val host: String,
    val port: String,
    val name: String,
    val username: String,
    val password: String,
    val jdbcUrl: String,
    val user: String,
)