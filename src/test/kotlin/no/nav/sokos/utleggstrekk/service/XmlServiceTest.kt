package no.nav.sokos.utleggstrekk.service

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import no.nav.sokos.utleggstrekk.database.model.TrekkTable
import no.nav.sokos.utleggstrekk.service.NyXmlService.toXml
import no.nav.sokos.utleggstrekk.util.TestUtilFunctions
import java.lang.String.valueOf
import java.time.LocalDateTime

internal class XmlServiceTest :
    BehaviorSpec({

        Given("Vi har et XML objekt") {
            val innrapporteringTrekkObjekt =
                InnrapporteringTrekk(
                    aksjonskode = "NY",
                    navTrekkId = "1234567",
                    kreditorIdTss = "80000123456",
                    kreditorTrekkId = "SIAN6212486",
                    kreditorOrgnr = "00913876725",
                    kontonr = "32073436388",
                    debitorId = "01020312345",
                    kodeTrekktype = "KRED",
                    kodeTrekkAlternativ = "SALM",
                    kid = "8000001284050805",
                    kreditorsRef = "T2023-285001",
                    saldo = "4598.37",
                    prioritetFomDato = "2024-05-06+02:00",
                    gyldigTomDato = "2026-05-06+02:00",
                    periode =
                        Periode(
                            periodeFomDato = "2025-09-01+02:00",
                            periodeTomDato = "2025-09-30+02:00",
                            sats = "4598.37",
                        ),
                )

            val xmlObjekt = OSUtleggsTrekk(innrapporteringTrekkObjekt)
            then("skal dette parses korrekt til XML") {
                val xmlAsString = xmlObjekt.toXml()
                val exampleXml = TestUtilFunctions.fileAsString("/nyxml.xml")
                xmlAsString shouldBe exampleXml
            }
        }
        Given("Vi mottar et trekk") {
            val trekk =
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
                    trekkbelop = null,
                    kid = "12345678901234567890",
                    kontonummer = "12341212345",
                    corrid = "corrID_1A",
                    status = "MOTTATT",
                    tidspunktMottatt = LocalDateTime.of(2023, 12, 1, 12, 0),
                    tidspunktSisteStatus = LocalDateTime.of(2023, 12, 1, 12, 0),
                    tidspunktOpprettet = LocalDateTime.of(2023, 12, 1, 12, 0),
                )
            and("det er trekkbeløp") {
                val trekkMedBelop = trekk.copy(trekkbelop = 1000.0, trekkprosent = null)
                `when`("XML objekt genereres") {
                    val trekkMedBelopXmlObjekt = NyXmlService.createNyTrekkXMLObjects(trekkMedBelop)
                    then("skal sats være lik trekkbeløp") {
                        trekkMedBelopXmlObjekt.dokument.periode.sats shouldBe "1000.00"
                    }
                    then("skal kodeTrekkAlternativ være LOPM") {
                        trekkMedBelopXmlObjekt.dokument.kodeTrekkAlternativ shouldBe valueOf(NyXmlService.KodeTrekkAlternativ.LOPM)
                    }
                }
            }

            and("det er trekkprosent") {
                val trekkMedProsent = trekk.copy(trekkprosent = 15.0, trekkbelop = null)
                `when`("XML objekt genereres") {
                    val trekkMedProsentXmlObjekt = NyXmlService.createNyTrekkXMLObjects(trekkMedProsent)
                    then("skal sats være lik trekkprosent") {
                        trekkMedProsentXmlObjekt.dokument.periode.sats shouldBe "15.00"
                    }
                    then("skal kodeTrekkAlternativ være LOPP") {
                        trekkMedProsentXmlObjekt.dokument.kodeTrekkAlternativ shouldBe valueOf(NyXmlService.KodeTrekkAlternativ.LOPP)
                    }
                }
            }
        }
    })