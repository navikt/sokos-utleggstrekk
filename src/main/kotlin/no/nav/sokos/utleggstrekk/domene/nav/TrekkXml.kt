package no.nav.sokos.utleggstrekk.domene.nav

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement
import kotlinx.datetime.LocalDateTime

@JacksonXmlRootElement(localName = "MsgHead")
data class TrekkXml(
    @field:JacksonXmlProperty(isAttribute = true, localName = "xmlns")
    val xmlns1: String = "http://www.kith.no/xmlstds/msghead/2006-05-24",
    @field:JacksonXmlProperty(isAttribute = true, localName = "xmlns:ivt")
    val xmlns2: String = "http://www.kith.no/xmlstds/nav/innrapporteringtrekk/2010-02-04",
    @field:JacksonXmlProperty(isAttribute = true, localName = "xmlns:fk1")
    val xmlns3: String = "http://www.kith.no/xmlstds/felleskomponent1",
    @field:JacksonXmlProperty(isAttribute = true, localName = "xmlns:ns4")
    val xmlns4: String = "http://www.kith.no/xmlstds",
    @field:JacksonXmlProperty(localName = "MsgInfo")
    val msgInfo: MsgInfo,
    @field:JacksonXmlProperty(localName = "Document")
    val document: Document,
)

data class MsgInfo(
    @field:JacksonXmlProperty(localName = "Type")
    val type: VDN = VDN(v = "INNRAPPORTERING_TREKK", dn = "Innrapportering av trekk"),
    @field:JacksonXmlProperty(localName = "MIGversion")
    val migVersion: String = "v1.2 2006-05-24",
    @field:JacksonXmlProperty(localName = "GenDate")
    val genDate: String = LocalDateTime.toString(),
    @field:JacksonXmlProperty(localName = "MsgId")
    val msgId: String,
    @field:JacksonXmlProperty(localName = "Ack")
    val ack: VDN = VDN(v = "J", dn = "Ja"),
    @field:JacksonXmlProperty(localName = "Sender")
    val sender: TrekkSenderReceiver,
    @field:JacksonXmlProperty(localName = "Receiver")
    val receiver: TrekkSenderReceiver
)

data class TrekkSenderReceiver(
    @field:JacksonXmlProperty(localName = "Organisation")
    val organisation: Organisation,
)

data class Organisation(
    @field:JacksonXmlProperty(localName = "OrganisationName")
    val organisationName: String,
    @field:JacksonXmlProperty(localName = "Ident")
    val ident: Ident,
    @field:JacksonXmlProperty(localName = "Adress")
    val address: AddressType1,
)

data class Ident(
    @field:JacksonXmlProperty(localName = "Id")
    val id: String,
    @field:JacksonXmlProperty(localName = "TypeId")
    val typeId: VSDN
)

data class AddressType1(
    @field:JacksonXmlProperty(localName = "StreetAdr")
    val streetAdress: String,
    @field:JacksonXmlProperty(localName = "PostalCode")
    val postalCode: String,
    @field:JacksonXmlProperty(localName = "City")
    val city: String,
    @field:JacksonXmlProperty(localName = "County")
    val county: VDN
)

data class Document(
    @field:JacksonXmlProperty(localName = "ContentDescription")
    val contentDescription: String = "Innrapportering av trekk",
    @field:JacksonXmlProperty(localName = "Consent")
    val consent: VSDN = VSDN(v = "1", s = "2.16.578.1.12.4.1.1.9064", dn = ""),
    @field:JacksonXmlProperty(localName = "RefDoc")
    val refDoc: RefDoc,
)

data class RefDoc(
    @field:JacksonXmlProperty(localName = "IssueDate")
    val issueDate: VDN = VDN(v = "2011-05-27T12:47:38", dn = ""),
    @field:JacksonXmlProperty(localName = "MsgType")
    val msgType: VDN = VDN(v = "XML", dn = "XML-instans"),
    @field:JacksonXmlProperty(localName = "Content")
    val content: Content
)

data class Content(
    @field:JacksonXmlProperty(localName = "ivt:InnrapporteringTrekk")
    val innrapporteringTrekk: InnrapporteringTrekk
)

data class InnrapporteringTrekk(
    @field:JacksonXmlProperty(localName = "ivt:Aksjonskode")
    val aksjonskode: VDN,
    @field:JacksonXmlProperty(localName = "ivt:Identifisering")
    val identifisering: Identifisering,
    @field:JacksonXmlProperty(localName = "ivt:Trekk")
    val trekk: Trekk,
    @field:JacksonXmlProperty(localName = "ivt:Periode")
    val periode: Periode,
    @field:JacksonXmlProperty(localName = "ivt:Kreditor")
    val kreditor: Kreditor,
    @field:JacksonXmlProperty(localName = "ivt:Namsmann")
    val namsmann: Namsmann,
)

data class Identifisering(
    @field:JacksonXmlProperty(localName = "ivt:KreditorTrekkId")
    val kreditorTrekkId: String,
    @field:JacksonXmlProperty(localName = "ivt:DebitorId")
    val debitorId: DebitorId,
)

data class DebitorId(
    @field:JacksonXmlProperty(localName = "fk1:Id")
    val id: String,
    @field:JacksonXmlProperty(localName = "fk1:TypeId")
    val typeId: VSDN = VSDN(v = "FNR", s = "2.16.578.1.12.4.1.1.8116", dn = "Fødselsnummer")
)

data class Trekk(
    val kodeTrekktype: VDN,
    val kodeTrekkAlternativ: VDN,
    val sats: V,
    val saldo: V,
    val prioritetFomDato: String,
    val gyldigTomDato: String,
)

data class Periode(
    @field:JacksonXmlProperty(localName = "ivt:PeriodeFomDato")
    val periodeFomDato: String,
    @field:JacksonXmlProperty(localName = "ivt:PeriodeTomDato")
    val periodeTomDato: String
)

data class Kreditor(
    @field:JacksonXmlProperty(localName = "ivt:OrgNr")
    val orgNr: Ident,
    @field:JacksonXmlProperty(localName = "ivt:Navn")
    val navn: String,
    @field:JacksonXmlProperty(localName = "ivt:Adresse")
    val adresse: AddressType2,
    @field:JacksonXmlProperty(localName = "ivt:Ref")
    val ref: String,
    @field:JacksonXmlProperty(localName = "ivt:Kontonr")
    val kontonr: String,
    @field:JacksonXmlProperty(localName = "ivt:KID")
    val kid: String,
)

data class Namsmann(
    @field:JacksonXmlProperty(localName = "ivt:OrgNr")
    val orgNr: Ident
)

data class AddressType2(
    val type: VDN,
    @field:JacksonXmlProperty(localName = "StreetAdr")
    val streetAdress: String,
    @field:JacksonXmlProperty(localName = "PostalCode")
    val postalCode: String,
    @field:JacksonXmlProperty(localName = "Country")
    val country: VDN
)

data class V(
    @field:JacksonXmlProperty(isAttribute = true, localName = "V")
    val v: String,
)

data class VDN(
    @field:JacksonXmlProperty(isAttribute = true, localName = "V")
    val v: String,
    @field:JacksonXmlProperty(isAttribute = true, localName = "DN")
    val dn: String
)

data class VSDN(
    @field:JacksonXmlProperty(isAttribute = true, localName = "V")
    val v: String,
    @field:JacksonXmlProperty(isAttribute = true, localName = "S")
    val s: String,
    @field:JacksonXmlProperty(isAttribute = true, localName = "DN")
    val dn: String
)

