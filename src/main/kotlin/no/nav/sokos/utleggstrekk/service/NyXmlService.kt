package no.nav.sokos.utleggstrekk.service

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import no.nav.sokos.utleggstrekk.database.model.TrekkTable
import java.lang.String.valueOf

class NyXmlService {
    fun createNyTrekkXMLObjects(trekk: TrekkTable) {
        val (sats, kodeTrekkAlternativ) =
            if (trekk.trekkbelop == null) {
                Pair(trekk.trekkprosent!!, KodeTrekkAlternativ.LOPP)
            } else {
                Pair(trekk.trekkbelop, KodeTrekkAlternativ.LOPM)
            }

        val innrapporteringTrekk =
            InnrapporteringTrekk(
                kreditorTrekkId = trekk.trekkid,
                kontonr = trekk.kontonummer,
                debitorId = trekk.skyldner,
                kid = trekk.kid,
                kreditorsRef = trekk.trekkid,
                kodeTrekkAlternativ = valueOf(kodeTrekkAlternativ),
                periode = Periode(trekk.startPeriode, trekk.sluttPeriode, sats),
            )
    }

    private enum class KodeTrekkAlternativ {
        LOPM,
        LOPP,
    }
}

@Serializable
@XmlSerialName("dokument")
data class OSUtleggsTrekk(
    val dokument: InnrapporteringTrekk,
)

@Serializable
@XmlSerialName("innrapporteringTrekk")
// BLOCK COMMENTS ER FRA CHAT MED ENDRE!!
data class InnrapporteringTrekk(
    /*
         aksjonskode kan ha NY, ENDR (endring av feks sats), ENRS (endring av restsaldo), KANS (kansellering) og OPPH (opphør).
        Jeg er usikker på når ENRS skal brukes i stedet for ENDR, og når OPPH skal brukes i stedet for KANS. Men dette finner vi ut etterhvert
     */
    @XmlElement val aksjonskode: String = "NY",
    @XmlElement val kreditorTrekkId: String,
    @XmlElement val kontonr: String,
    @XmlElement val debitorId: String,
    // LOPM hvis beløp, LOPP hvis prosent
    @XmlElement val kodeTrekkAlternativ: String,
    @XmlElement val kid: String,
    @XmlElement val kreditorsRef: String,
    val periode: Periode,
    /*
     * saldo er beløpet som debitor skylder. Brukes ved saldo-trekk, ikke løpende trekk.
     * Slik at hvis en skylder 10000kr og skal betale 1000pr måned vil felt saldo være lik 10000,
     * mens sats vil være 1000 og kodeTrekkAlternativ være SALM.
     *  Hvis de 10000 skal betales med 15 prosent pr måned vil saldo være lik 10000,
     * mens sats vil være 15 og kodeTrekkAlternativ være SALP.
     * Men det finnes også trekk som er løpende. Feks husleie.
     * Der er det ingen saldo (altså = 0), mens sats vil da være månedsleien på feks 5000.
     * I disse tilfellene er kodeTrekkAlternativ gjerne LOPM.
     *
     * Dersom trekkene fra skatt er saldotrekk så må de sende denne.
     *  Dersom de ikke gjør det vil vi oppfatte trekket som et løpende trekk.
     *  Og dersom de bare har månedstrekk og ikke saldo vil kodeTrekkAlternativ være LOPM.
     *
     *
     * OVERSATT:
     * Vi får ikke saldo fra skatt, så disse trekkene anses som løpende
     * saldo vil derfor ALLTID være 0
     *
     * Sats skal settes til likt trekkbeløp eller trekkprosent
     * ved trekkbeløp: kodeTrekkAlternativ = LOPM
     * ved trekkprosent: kodeTrekkAlternativ = LOPP
     * */
    @XmlElement private val saldo: String = "0.0",
    @XmlElement private val prioritetFomDato: String = "",
    @XmlElement private val gyldigTomDato: String = "",
    @XmlElement private val kreditorIdTss: String = "00987654321",
    @XmlElement private val kodeTrekktype: String = "KRED",
    /*
    navTrekkId er den id'en som OS identifiserer trekket med.
    Vi trenger ikke sende navTrekkId.
    Feltet kan brukes til å identifisere trekket ved endringer.
     Men vi (dere) vil sikkert bruke kombinasjonen kreditorIdTss og kreditorTrekkId.
     */
    @XmlElement private val navTrekkId: String = "$kreditorIdTss-$kreditorTrekkId",
    @XmlElement private val kreditorOrgnr: String = "00987654321",
)

@Serializable
@XmlSerialName("periode")
data class Periode(
    @XmlElement val periodeFomDato: String,
    @XmlElement val periodeTomDato: String,
    @XmlElement val sats: Double,
)