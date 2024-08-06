package no.nav.sokos.utleggstrekk.service

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import no.nav.sokos.utleggstrekk.database.model.TrekkTable

class NyXmlService {
    fun createNyTrekkXMLObjects(trekk: TrekkTable) {
        val sats =
            when {
                trekk.trekkbelop == null -> trekk.trekkprosent!!
                else -> trekk.trekkbelop
            }
        val periode = Periode(trekk.startPeriode, trekk.sluttPeriode, sats)

        val innrapporteringTrekk =
            InnrapporteringTrekk(
                navTrekkId = "nav trekk id",
                kreditorIdTss = "00987654321",
                kreditorTrekkId = trekk.trekkid,
                kontonr = trekk.kontonummer,
                debitorId = trekk.skyldner,
                kid = trekk.kid,
                kreditorsRef = trekk.trekkid,
                saldo = "1000.00",
                prioritetFomDato = "prioritet fom dato",
                gyldigTomDato = "gyldig tomdato",
                periode = periode,
            )
    }
}

@Serializable
@XmlSerialName("dokument")
data class OSUtleggsTrekk(
    val dokument: InnrapporteringTrekk,
)

@Serializable
@XmlSerialName("innrapporteringTrekk")
data class InnrapporteringTrekk(
    @XmlElement val aksjonskode: String = "NY",
    @XmlElement val navTrekkId: String,
    @XmlElement val kreditorIdTss: String,
    @XmlElement val kreditorTrekkId: String,
    @XmlElement val kreditorOrgnr: String = "00987654321",
    @XmlElement val kontonr: String,
    @XmlElement val debitorId: String,
    @XmlElement val kodeTrekktype: String = "KRED",
    @XmlElement val kodeTrekkAlternativ: String = "SALM",
    @XmlElement val kid: String,
    @XmlElement val kreditorsRef: String,
    @XmlElement val saldo: String,
    @XmlElement val prioritetFomDato: String,
    @XmlElement val gyldigTomDato: String,
    val periode: Periode,
)

@Serializable
@XmlSerialName("periode")
data class Periode(
    @XmlElement val periodeFomDato: String,
    @XmlElement val periodeTomDato: String,
    @XmlElement val sats: Double,
)