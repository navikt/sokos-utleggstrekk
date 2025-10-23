@file:OptIn(ExperimentalTime::class)

package no.nav.sokos.utleggstrekk.domene.ske

import kotlin.time.ExperimentalTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Trekkpaalegg(
    val trekkid: String,
    val sekvensnummer: Int,
    val trekkversjon: Int,
    val opprettet: String,
    val saksnummer: String,
    val trekkpliktig: String,
    val skyldner: String,
    val trekkstatus: Trekkstatus,
    val trekkstoerrelseForPeriode: List<TrekkstorrelseForPeriode>,
    val betalingsinformasjon: Betalingsinformasjon,
)

@Serializable
data class Trekkprosent(val trekkprosent: Double? = null)

@Serializable
data class Trekkbeloep(val trekkbeloep: Double? = null)

@Serializable
enum class Trekkstatus {
    @SerialName("aktiv")
    AKTIV,

    @SerialName("avsluttet")
    AVSLUTTET,
}

@Serializable
data class TrekkstorrelseForPeriode(
    val startdato: String,
    val sluttdato: String? = null,
    val trekkbeloep: Trekkbeloep? = null,
    val trekkprosent: Trekkprosent? = null,
)

@Serializable
data class Betalingsinformasjon(
    val betalingsmottaker: String,
    val kidnummer: String,
    val kontonummer: String,
)
