package no.nav.sokos.utleggstrekk

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator
import io.kotest.core.spec.style.FunSpec
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

internal class KenTester : FunSpec({

    test("db timeformats") {
        val strDato = "2024-06-17T13:33:05.672Z"
    }

    test("finne  ut av xml attributter") {
        @JacksonXmlRootElement(localName = "HereWeGo")
        data class TestXml(
            val hei: String,
            @field:JacksonXmlProperty(isAttribute = true, localName = "DN")
            val dn: String,
            val hallo: String,
        )

        val xmlMapper =
            XmlMapper(
                JacksonXmlModule().apply { },
            ).apply {
                enable(SerializationFeature.INDENT_OUTPUT)
                enable(SerializationFeature.WRAP_ROOT_VALUE)
                enable(ToXmlGenerator.Feature.WRITE_XML_DECLARATION)
            }

        val xmlData =
            TestXml(
                hei = "Hurra",
                dn = "ABC",
                hallo = "nå ere juligjen",
            )
        val xml = xmlMapper.writeValueAsString(xmlData)
        println(xml)
    }

    test("tester xml klasser for trekk") {
        val xmlMapper =
            XmlMapper(
                JacksonXmlModule().apply { },
            ).apply {
                enable(SerializationFeature.INDENT_OUTPUT)
                enable(SerializationFeature.WRAP_ROOT_VALUE)
                enable(ToXmlGenerator.Feature.WRITE_XML_DECLARATION)
            }

        val xmlData =
            TrekkXml(
                msgInfo =
                    MsgInfo(
                        msgId = "En gguid",
                    ),
                // msgInfo
                document =
                    Document(
                        refDoc =
                            RefDoc(
                                content =
                                    Content(
                                        innrapporteringTrekk =
                                            InnrapporteringTrekk(
                                                aksjonskode = VDN(v = "NY", dn = "Nytt trekk"),
                                                identifisering =
                                                    Identifisering(
                                                        kreditorTrekkId = "TREKKID_000001",
                                                        debitorId =
                                                            DebitorId(
                                                                id = "09876543219",
                                                                typeId = VSDN(v = "FNR", s = "2.16.578.1.12.4.1.1.8116", dn = "Fødselsnummer"),
                                                            ),
                                                    ),
                                                trekk =
                                                    Trekk(
                                                        kodeTrekktype = VDN(v = "KRED", dn = "Deknings.§ bokstav f"),
                                                        kodeTrekkAlternativ = VDN(v = "SALM", dn = "Saldotrekk månedssats"),
                                                        sats = V(v = "12345.50"),
                                                        saldo = V(v = "10000.00"),
                                                        prioritetFomDato = "2024-05-06+02:00",
                                                        gyldigTomDato = "2026-05-06+02:00",
                                                    ),
                                                periode =
                                                    Periode(
                                                        periodeFomDato = "2025-09-01+02:00",
                                                        periodeTomDato = "2025-09-30+02:00",
                                                    ),
                                                kreditor =
                                                    Kreditor(
                                                        ref = "Dette er kreditorREF",
                                                        kontonr = "Kontonummer12345",
                                                        kid = "kid1234567890",
                                                    ),
                                            ), // innrapporteringTrekk
                                    ), // content
                            ), // refDoc
                    ), // document
            ) // xmldata

        val xml = xmlMapper.writeValueAsString(xmlData)
        println(xml)
    }
})