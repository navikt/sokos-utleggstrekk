package no.nav.sokos.utleggstrekk.service

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator
import no.nav.sokos.utleggstrekk.database.model.TrekkTable
import no.nav.sokos.utleggstrekk.domene.nav.Content
import no.nav.sokos.utleggstrekk.domene.nav.DebitorId
import no.nav.sokos.utleggstrekk.domene.nav.Document
import no.nav.sokos.utleggstrekk.domene.nav.Identifisering
import no.nav.sokos.utleggstrekk.domene.nav.InnrapporteringTrekk
import no.nav.sokos.utleggstrekk.domene.nav.Kreditor
import no.nav.sokos.utleggstrekk.domene.nav.MsgInfo
import no.nav.sokos.utleggstrekk.domene.nav.Periode
import no.nav.sokos.utleggstrekk.domene.nav.RefDoc
import no.nav.sokos.utleggstrekk.domene.nav.Trekk
import no.nav.sokos.utleggstrekk.domene.nav.TrekkXml
import no.nav.sokos.utleggstrekk.domene.nav.V
import no.nav.sokos.utleggstrekk.domene.nav.VDN
import no.nav.sokos.utleggstrekk.domene.nav.VSDN

object XmlService {

    fun generateXmlStringListFromTrekkXmlList(xmlDataList: List<TrekkXml>): List<String>{
        val xmlMapper = XmlMapper(
            JacksonXmlModule().apply {  }
        ).apply {
            enable(SerializationFeature.INDENT_OUTPUT)
            enable(SerializationFeature.WRAP_ROOT_VALUE)
            enable(ToXmlGenerator.Feature.WRITE_XML_DECLARATION)
        }
        return xmlDataList.map {
            println("Mapper ${it.msgInfo.msgId}")
            val xml = xmlMapper.writeValueAsString(it)
            println(xml)
            xml
        }
    }

    fun createTrekkXml(trekk: TrekkTable): TrekkXml {
        val sats = when {
            trekk.trekkbelop == null -> trekk.trekkprosent!!
            else -> trekk.trekkbelop
        }
        return TrekkXml(
                msgInfo = MsgInfo(
                    msgId = trekk.corrid,
                ),
                document = Document(
                    refDoc = RefDoc(
                        content = Content(
                            innrapporteringTrekk = InnrapporteringTrekk(
                                aksjonskode = VDN(v = "NY", dn = "Nytt trekk"),  //TODO Dette må sikker være dynamisk.(Ny, endring eller  stopp)
                                identifisering = Identifisering(
                                    kreditorTrekkId = trekk.trekkid,
                                    debitorId = DebitorId(
                                        id = trekk.skyldner,
                                        typeId = VSDN(v = "FNR", s = "2.16.578.1.12.4.1.1.8116", dn = "Fødselsnummer") //TODO verifisere om debitorId kan være annet enn fnr??
                                    ),
                                ),
                                trekk = Trekk(
                                    kodeTrekktype = VDN(v = "KRED", dn = "Deknings.§ bokstav f"),  //TODO Verifiosere hvilke kode trekktype som finnes og hva som definerer de
                                    kodeTrekkAlternativ = VDN(v = "SALM", dn = "Saldotrekk månedssats"),  //TODO HVike alternativeTrekkalternativer finnes
                                    sats = V(v = "$sats"),
                                    saldo = V(v = "10000.00"), //TODO Hva F bvruker vi her hvis det  er % sats, ved beløp kan vi kange med antall måneder?
                                    prioritetFomDato = trekk.startPeriode,  //TODO AVklare/verifisere hva er/hvilket felt skal bukes
                                    gyldigTomDato = trekk.sluttPeriode   //TODO AVklare/verifisere hva er/hvilket felt skal bukes
                                ),
                                periode = Periode(
                                    periodeFomDato = trekk.startPeriode,  //TODO fra trekket Start ??
                                    periodeTomDato = trekk.sluttPeriode //TODO  fra trekket Slutt ??
                                ),
                                kreditor = Kreditor( //TODO  Denne vil vel alltid være skatteetaten? så den kan gjøres som default? evt samme som sender??
                                    ref = trekk.trekkid,
                                    kontonr = trekk.kontonummer,
                                    kid = trekk.kid
                                ),
                            ) //innrapporteringTrekk
                        ) //content
                    ) //refDoc
                ) //document
            ) //xmldata/TrekkXml
    }
}