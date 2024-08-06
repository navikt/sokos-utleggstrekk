package no.nav.sokos.utleggstrekk.service

import io.kotest.core.spec.style.FunSpec
import nl.adaptivity.xmlutil.XmlDeclMode
import nl.adaptivity.xmlutil.core.XmlVersion
import nl.adaptivity.xmlutil.serialization.XML
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

internal class XMLParseTest :
    FunSpec({

        test("nyxml parse") {
            val testObj = XML.decodeFromString(OSUtleggsTrekk.serializer(), xmlToParse)

            val testStr =
                XML {
                    xmlVersion = XmlVersion.XML10
                    xmlDeclMode = XmlDeclMode.Charset
                    indent = 4
                }.encodeToString(OSUtleggsTrekk.serializer(), testObj)
            println(testStr)
        }
    })

val gammelXmlObj =
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
val xmlToParse =
    """
    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
    <dokument>
        <innrapporteringTrekk>
            <aksjonskode>NY</aksjonskode>
            <navTrekkId>1234567</navTrekkId>
            <kreditorIdTss>80000123456</kreditorIdTss>
            <kreditorTrekkId>SIAN6212486</kreditorTrekkId>
            <kreditorOrgnr>00913876725</kreditorOrgnr>
            <kontonr>32073436388</kontonr>
            <debitorId>01020312345</debitorId>
            <kodeTrekktype>KRED</kodeTrekktype>
            <kodeTrekkAlternativ>SALM</kodeTrekkAlternativ>
            <kid>8000001284050805</kid>
            <kreditorsRef>T2023-285001</kreditorsRef>
            <saldo>4598.37</saldo>
            <prioritetFomDato>2024-05-06+02:00</prioritetFomDato>
            <gyldigTomDato>2026-05-06+02:00</gyldigTomDato>
            <periode>
                <periodeFomDato>2025-09-01+02:00</periodeFomDato>
                <periodeTomDato>2025-09-30+02:00</periodeTomDato>
                <sats>4598.37</sats>
            </periode>
        </innrapporteringTrekk>
    </dokument>
    """.trimIndent()