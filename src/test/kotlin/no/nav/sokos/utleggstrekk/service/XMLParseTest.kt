package no.nav.sokos.utleggstrekk.service

import io.kotest.core.spec.style.FunSpec
import nl.adaptivity.xmlutil.XmlDeclMode
import nl.adaptivity.xmlutil.core.XmlVersion
import nl.adaptivity.xmlutil.serialization.XML
import no.nav.sokos.utleggstrekk.database.model.TrekkTable
import java.time.LocalDateTime

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

        test("trekktable til xml objekt") {
            val xmlObjekter =
                trekkTableListGenerert.map {
                    NyXmlService.createNyTrekkXMLObjects(it)
                }

            xmlObjekter.forEach { println(it) }
            xmlObjekter.forEach {
                val testStr =
                    XML {
                        xmlVersion = XmlVersion.XML10
                        xmlDeclMode = XmlDeclMode.Charset
                        indent = 4
                    }.encodeToString(OSUtleggsTrekk.serializer(), it)
                println(testStr)
            }
        }
    })

val trekkTableListGenerert =
    listOf(
        TrekkTable(
            trekktableid = 1,
            trekkid = "1",
            sekvensnr = 1,
            trekkversjon = 1,
            trekkopprettet = LocalDateTime.of(2023, 12, 1, 12, 0),
            trekkpliktig = "987654321",
            skyldner = "12345678901",
            trekkstatus = "AKTIV",
            startPeriode = "2024-01-01",
            sluttPeriode = "2024-11-30",
            trekkbelop = 1000.0,
            kid = "12345678901234567890",
            kontonummer = "12341212345",
            corrid = "corrID_1A",
            status = "MOTTATT",
            tidspunktMottatt = LocalDateTime.of(2023, 12, 1, 12, 0),
            tidspunktSisteStatus = LocalDateTime.of(2023, 12, 1, 12, 0),
            tidspunktOpprettet = LocalDateTime.of(2023, 12, 1, 12, 0),
        ),
        TrekkTable(
            trekktableid = 1,
            trekkid = "1",
            sekvensnr = 1,
            trekkversjon = 1,
            trekkopprettet = LocalDateTime.of(2023, 12, 1, 12, 0),
            trekkpliktig = "987654321",
            skyldner = "12345678901",
            trekkstatus = "AKTIV",
            startPeriode = "2025-01-01",
            sluttPeriode = "2025-05-31",
            trekkbelop = 1000.0,
            kid = "12345678901234567890",
            kontonummer = "12341212345",
            corrid = "corrID_1A",
            status = "MOTTATT",
            tidspunktMottatt = LocalDateTime.of(2023, 12, 1, 12, 0),
            tidspunktSisteStatus = LocalDateTime.of(2023, 12, 1, 12, 0),
            tidspunktOpprettet = LocalDateTime.of(2023, 12, 1, 12, 0),
        ),
        TrekkTable(
            trekktableid = 1,
            trekkid = "1",
            sekvensnr = 1,
            trekkversjon = 1,
            trekkopprettet = LocalDateTime.of(2023, 12, 1, 12, 0),
            trekkpliktig = "987654321",
            skyldner = "12345678901",
            trekkstatus = "AKTIV",
            startPeriode = "2025-08-01",
            sluttPeriode = "2025-12-31",
            trekkbelop = 1000.0,
            kid = "12345678901234567890",
            kontonummer = "12341212345",
            corrid = "corrID_1A",
            status = "MOTTATT",
            tidspunktMottatt = LocalDateTime.of(2023, 12, 1, 12, 0),
            tidspunktSisteStatus = LocalDateTime.of(2023, 12, 1, 12, 0),
            tidspunktOpprettet = LocalDateTime.of(2023, 12, 1, 12, 0),
        ),
        TrekkTable(
            trekktableid = 1,
            trekkid = "1",
            sekvensnr = 1,
            trekkversjon = 1,
            trekkopprettet = LocalDateTime.of(2023, 12, 1, 12, 0),
            trekkpliktig = "987654321",
            skyldner = "12345678901",
            trekkstatus = "AKTIV",
            startPeriode = "2026-02-01",
            sluttPeriode = "2025-04-30",
            trekkbelop = 1000.0,
            kid = "12345678901234567890",
            kontonummer = "12341212345",
            corrid = "corrID_1A",
            status = "MOTTATT",
            tidspunktMottatt = LocalDateTime.of(2023, 12, 1, 12, 0),
            tidspunktSisteStatus = LocalDateTime.of(2023, 12, 1, 12, 0),
            tidspunktOpprettet = LocalDateTime.of(2023, 12, 1, 12, 0),
        ),
        TrekkTable(
            trekktableid = 2,
            trekkid = "2",
            sekvensnr = 2,
            trekkversjon = 1,
            trekkopprettet = LocalDateTime.of(2023, 12, 1, 12, 0),
            trekkpliktig = "987654322",
            skyldner = "12345678902",
            trekkstatus = "AKTIV",
            startPeriode = "2024-01-01",
            sluttPeriode = "2024-04-30",
            trekkbelop = 1000.0,
            kid = "12345678901234567892",
            kontonummer = "12341212342",
            corrid = "corrID_2A",
            status = "MOTTATT",
            tidspunktMottatt = LocalDateTime.of(2023, 12, 1, 12, 0),
            tidspunktSisteStatus = LocalDateTime.of(2023, 12, 1, 12, 0),
            tidspunktOpprettet = LocalDateTime.of(2023, 12, 1, 12, 0),
        ),
        TrekkTable(
            trekktableid = 3,
            trekkid = "3",
            sekvensnr = 3,
            trekkversjon = 1,
            trekkopprettet = LocalDateTime.of(2023, 12, 1, 12, 0),
            trekkpliktig = "987654322",
            skyldner = "12345678902",
            trekkstatus = "AKTIV",
            startPeriode = "2024-01-01",
            sluttPeriode = "2026-04-30",
            trekkprosent = 15.0,
            kid = "12345678901234567892",
            kontonummer = "12341212342",
            corrid = "corrID_EA",
            status = "MOTTATT",
            tidspunktMottatt = LocalDateTime.of(2023, 12, 1, 12, 0),
            tidspunktSisteStatus = LocalDateTime.of(2023, 12, 1, 12, 0),
            tidspunktOpprettet = LocalDateTime.of(2023, 12, 1, 12, 0),
        ),
    )
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