package no.nav.sokos.utleggstrekk.service

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator
import no.nav.sokos.utleggstrekk.database.model.TrekkTable
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
import java.util.*

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
        //val sats = if (trekk.trekkbeloep!= null && trekk.trekkbeloep.trekkbeloep != null && trekk.trekkbeloep.trekkbeloep > 0.0)
        return TrekkXml(
                msgInfo = MsgInfo(
                    msgId = UUID.randomUUID().toString(),
                    sender = TrekkSenderReceiver(
                        organisation = Organisation(
                            organisationName = "Skatteetaten", //TODO Verifser/endre, statisk
                            ident = Ident(
                                id = "123456789",  //TODO Orgnummer til Sender, Statisk
                                typeId = VSDN(  //TODO Verifisere informasjonen, Statisk
                                    v = "ENH",
                                    s = "2.16.578.1.12.4.1.1.9051",
                                    dn = "Organisasjonsnummeret i  Enhetsregister (Brønnøysund)"
                                )
                            ),
                            address = AddressType1(
                                //TODO Legge inn riktig adresse, Statisk
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
                            organisationName = "NAV",  //TODO verifisere hviket navn som skal brukes, Statisk
                            ident = Ident(
                                id = "889640782",  //TODO Verifisere riktig orgnr, Statisk
                                typeId = VSDN(  //TODO Verifisere informasjonen, Statisk
                                    v = "ENH",
                                    s = "2.16.578.1.12.4.1.1.9051",
                                    dn = "Organisasjonsnummeret i  Enhetsregister (Brønnøysund)"
                                )
                            ),
                            address = AddressType1(
                                //TODO Verifisere/legge inn riktig adresse
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
                                aksjonskode = VDN(v = "NY", dn = "Nytt trekk"),  //TODO Dette må sikker være dynamisk.(Ny, endring eller  stopp)
                                identifisering = Identifisering(
                                    kreditorTrekkId = trekk.trekkid,
                                    debitorId = DebitorId(  //TODO Skyldner fra trekket
                                        id = trekk.skyldner,
                                        typeId = VSDN(v = "FNR", s = "2.16.578.1.12.4.1.1.8116", dn = "Fødselsnummer") //TODO verifisere om kan være annet
                                    ),
                                ),
                                trekk = Trekk(
                                    kodeTrekktype = VDN(v = "KRED", dn = "Deknings.§ bokstav f"),  //TODO Verifiosere hvilke kode som finnes og hva som definerer de
                                    kodeTrekkAlternativ = VDN(v = "SALM", dn = "Saldotrekk månedssats"),  //TODO HVike alternativer finnes
                                    sats = V(v = "12345.50"),  //TODO Fra trekket
                                    saldo = V(v = "10000.00"), //TODO Fra Trekket
                                    prioritetFomDato = "2024-05-06+02:00",  //TODO AVklare hva er
                                    gyldigTomDato = "2026-05-06+02:00"   //TODO Avklare hva er
                                ),
                                periode = Periode(
                                    periodeFomDato = "2025-09-01+02:00",  //TODO fra trekket
                                    periodeTomDato = "2025-09-30+02:00" //TODO  fra trekket
                                ),
                                kreditor = Kreditor( //TODO  Denne vil vel alltid være skatteetaten? så den kan gjøres som default?
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
                                ), //kreditor
                                namsmann = Namsmann(  //TODO Vil vi vite noe om denne?  Alltid SKE dette også?
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
    }
}