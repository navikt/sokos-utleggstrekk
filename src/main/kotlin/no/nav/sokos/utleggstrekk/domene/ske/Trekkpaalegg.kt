package no.nav.sokos.utleggstrekk.domene.ske

import java.time.LocalDate

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import no.nav.sokos.utleggstrekk.utils.Validation.isDate
import no.nav.sokos.utleggstrekk.utils.Validation.isDateTime
import no.nav.sokos.utleggstrekk.utils.Validation.isDigits
import no.nav.sokos.utleggstrekk.utils.Validation.isSafeText
import no.nav.sokos.utleggstrekk.utils.Validation.isValidKid

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
data class Trekkprosent(val trekkprosent: Double)

@Serializable
data class Trekkbeloep(val trekkbeloep: Double)

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
) {
    init {
        require(hasProsent() xor hasBeloep()) { "En trekkperiode må enten ha prosent eller beløp" }
    }

    fun hasProsent() = trekkprosent != null

    fun hasBeloep() = trekkbeloep != null
}

@Serializable
data class Betalingsinformasjon(
    val betalingsmottaker: String,
    val kidnummer: String,
    val kontonummer: String,
)

fun Trekkpaalegg.validate() {
    try {
        require(trekkid.length > 0 && trekkid.isSafeText()) { "trekkid har ugyldige tegn" }
        require(saksnummer.isSafeText()) { "saksnummer har ugyldige tegn" }
        require(trekkversjon > 0) { "trekkversjon har ulovlig verdi " }
        require(sekvensnummer > 0) { "sekvensnummer har ulovlig verdi" }
        require(opprettet.isDateTime()) { "opprettet har ulovlig verdi" }
        require(trekkpliktig.length == 9 && trekkpliktig.isDigits()) { "trekkpliktig har ulovlig verdi" }
        require(skyldner.length == 11 && skyldner.isDigits()) { "skyldner har ulovlig verdi" }
        trekkstoerrelseForPeriode.forEach(TrekkstorrelseForPeriode::validate)
        betalingsinformasjon.validate()
    } catch (e: Exception) {
        throw IllegalArgumentException("Trekkpaalegg validation failed: trekkid: $trekkid, trekkversjon: $trekkversjon, sekvensnummer: $sekvensnummer", e)
    }
}

fun Betalingsinformasjon.validate() {
    require(betalingsmottaker.length == 9 && betalingsmottaker.isDigits()) { "Betalingsmottaker har ulovlig verdi" }
    require(kidnummer.isValidKid()) { "kidnummer har ulovlig verdi" }
    require(kontonummer.length == 11 && kontonummer.isDigits()) { "kontonummer har ulovlig verdi" }
}

fun TrekkstorrelseForPeriode.validate() {
    if (trekkprosent != null) require(trekkprosent.trekkprosent in 0.0..100.0) { "Trekkprosent har ulovlig verdi" }
    if (trekkbeloep != null) require(trekkbeloep.trekkbeloep >= 0.0) { "Trekkbeloep ulovlig verdi" }
    require(startdato.isDate()) { "Startdato har ulovlig verdi" }
    if (sluttdato != null) {
        require(sluttdato.isDate()) { "Sluttdato har ulovlig verdi" }
        require(LocalDate.parse(startdato).isBefore(LocalDate.parse(sluttdato))) { "Sluttdato må være etter startdato" }
    }
}