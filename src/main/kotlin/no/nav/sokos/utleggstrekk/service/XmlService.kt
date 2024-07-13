package no.nav.sokos.utleggstrekk.service

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator
import no.nav.sokos.utleggstrekk.domene.nav.AddressType1
import no.nav.sokos.utleggstrekk.domene.nav.AddressType2
import no.nav.sokos.utleggstrekk.domene.nav.Content
import no.nav.sokos.utleggstrekk.domene.nav.DebitorId
import no.nav.sokos.utleggstrekk.domene.nav.Document
import no.nav.sokos.utleggstrekk.domene.nav.Ident
import no.nav.sokos.utleggstrekk.domene.nav.Identifisering
import no.nav.sokos.utleggstrekk.domene.nav.InnrapporteringTrekk
import no.nav.sokos.utleggstrekk.domene.nav.Kreditor
import no.nav.sokos.utleggstrekk.domene.nav.MsgInfo
import no.nav.sokos.utleggstrekk.domene.nav.Namsmann
import no.nav.sokos.utleggstrekk.domene.nav.Organisation
import no.nav.sokos.utleggstrekk.domene.nav.Periode
import no.nav.sokos.utleggstrekk.domene.nav.RefDoc
import no.nav.sokos.utleggstrekk.domene.nav.Trekk
import no.nav.sokos.utleggstrekk.domene.nav.TrekkSenderReceiver
import no.nav.sokos.utleggstrekk.domene.nav.TrekkXml
import no.nav.sokos.utleggstrekk.domene.nav.V
import no.nav.sokos.utleggstrekk.domene.nav.VDN
import no.nav.sokos.utleggstrekk.domene.nav.VSDN
import no.nav.sokos.utleggstrekk.domene.ske.Utleggstrekk

object XmlService {

    fun generateXmlFromTrekk(trekkListe: List<Utleggstrekk>): String{
        val xmlMapper = XmlMapper(
            JacksonXmlModule().apply {  }
        ).apply {
            enable(SerializationFeature.INDENT_OUTPUT)
            enable(SerializationFeature.WRAP_ROOT_VALUE)
            enable(ToXmlGenerator.Feature.WRITE_XML_DECLARATION)
        }

        val xmlData = populateTrekkObjects(trekkListe)

        val xml = xmlMapper.writeValueAsString(xmlData)
        println(xml)
        return xml
    }

    private fun populateTrekkObjects(trekkListe: List<Utleggstrekk>): TrekkXml {
        val xmlData = TrekkXml(
            msgInfo = MsgInfo(
                msgId = "En gguid",
                sender = TrekkSenderReceiver(
                    organisation = Organisation(
                        organisationName = "Statens innkrevingssentral",
                        ident = Ident(
                            id = "123456789",
                            typeId = VSDN(
                                v = "ENH",
                                s = "some sort of version",
                                dn = "orgnummer beskrivelse"
                            )
                        ),
                        address = AddressType1(
                            streetAdress = "gateadresse",
                            postalCode = "Postkode",
                            city = "MO I RANA",
                            county = VDN(
                                v = "8601",
                                dn = "MO I RANA"
                            ),
                        ),
                    ),
                ),
                receiver = TrekkSenderReceiver(
                    organisation = Organisation(
                        organisationName = "NAV",
                        ident = Ident(
                            id = "889640782",
                            typeId = VSDN(
                                v = "ENH",
                                s = "some sort of version",
                                dn = "orgnummer beskrivelse"
                            )
                        ),
                        address = AddressType1(
                            streetAdress = "Fyrstikken",
                            postalCode = "0579",
                            city = "OSLO",
                            county = VDN(
                                v = "0589",
                                dn = "OSLO"
                            ),
                        ),
                    ),
                ),
            ), //msgInfo
            document = Document(
                refDoc = RefDoc(
                    content = Content(
                        innrapporteringTrekk = InnrapporteringTrekk(
                            aksjonskode = VDN(v = "NY", dn = "Nytt trekk"),
                            identifisering = Identifisering(
                                kreditorTrekkId = "TREKKID_000001",
                                debitorId = DebitorId(
                                    id = "09876543219",
                                    typeId = VSDN(v = "FNR", s = "2.16.578.1.12.4.1.1.8116", dn="Fødselsnummer")
                                ),
                            ),
                            trekk = Trekk(
                                kodeTrekktype = VDN(v = "KRED", dn = "Deknings.§ bokstav f"),
                                kodeTrekkAlternativ = VDN(v = "SALM", dn = "Saldotrekk månedssats"),
                                sats = V(v = "12345.50"),
                                saldo = V(v = "10000.00"),
                                prioritetFomDato = "2024-05-06+02:00",
                                gyldigTomDato = "2026-05-06+02:00"
                            ),
                            periode = Periode(
                                periodeFomDato = "2025-09-01+02:00",
                                periodeTomDato = "2025-09-30+02:00"
                            ),
                            kreditor = Kreditor(
                                orgNr = Ident(
                                    id = "00987654321",
                                    typeId = VSDN(v = "ENH", s = "2.16.578.1.12.4.1.1.9051", dn = "DN")
                                ),
                                navn = "Ropo Capital Norway AS",
                                adresse = AddressType2(
                                    type = VDN(v = "UM", dn = "adresse i SIAN"),
                                    streetAdress = "Postboks somwhere",
                                    postalCode = "1234",
                                    country = VDN(v = "NO", dn = "Norge")
                                ),
                                ref = "T2023-123456",
                                kontonr = "12345612345",
                                kid = "99999999999999999999"
                            ),
                            namsmann = Namsmann(
                                orgNr = Ident(
                                    id = "987654321",
                                    typeId = VSDN(v = "ENH", s = "2.16.578.1.12.4.1.1.9051", dn = "DN")
                                )
                            )
                        ) //innrapporteringTrekk
                    ) //content
                ) //refDoc
            ) //document
        ) //xmldata

        return xmlData
    }
}