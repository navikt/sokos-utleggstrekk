@file:OptIn(ExperimentalTime::class)

package no.nav.sokos.utleggstrekk.security.maskinporten

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Token(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("expires_in")
    val expiresIn: Long,
)

data class AccessToken(
    val accessToken: String,
    val expiresAt: Instant,
) {
    constructor(token: Token) : this(
        accessToken = token.accessToken,
        expiresAt = Clock.System.now().plus(token.expiresIn, DateTimeUnit.SECOND),
    )
}
