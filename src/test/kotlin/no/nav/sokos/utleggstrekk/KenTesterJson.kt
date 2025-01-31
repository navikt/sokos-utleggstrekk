package no.nav.sokos.utleggstrekk

import com.google.gson.Gson
import io.kotest.core.spec.style.FunSpec
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.sokos.utleggstrekk.database.model.TrekkPeriodeTable
import no.nav.sokos.utleggstrekk.database.model.TrekkpaleggTable
import no.nav.sokos.utleggstrekk.domene.ske.Trekkpaalegg
import no.nav.sokos.utleggstrekk.domene.toTrekkDokument
import org.testcontainers.shaded.com.google.common.reflect.TypeToken
import java.time.LocalDateTime

internal class KenTesterJson : FunSpec({

    test("db timeformats") {
        val strDato = "2024-06-17T13:33:05.672Z"
    }

    test("parsing av reposnse fra skatt") {
        println(bodyFraSkatt)
        val typeToken = object : TypeToken<List<Trekkpaalegg>>() {}.type
        val trekkpaalegg: List<Trekkpaalegg> = Gson().fromJson<List<Trekkpaalegg>>(bodyFraSkatt.toString(), typeToken)
        println("antall: ${trekkpaalegg.size}")
        trekkpaalegg.forEach {
            println("TrekkID: ${it.trekkid}")
            println("TrekkVersjon${it.trekkversjon}")
            println("fnr: ${it.skyldner}")
            println("trekkbeløp: ${it.trekkstoerrelseForPeriode[0].trekkbeloep.let { 
                if (it == null || it.trekkbeloep == null) java.sql.Types.NULL.toDouble()
                else it.trekkbeloep
            }}")
            println("trekkprosent: ${it.trekkstoerrelseForPeriode[0].trekkprosent.let { 
                if (it == null || it.trekkprosent == null) java.sql.Types.NULL.toDouble()
                else it.trekkprosent
            }}")
            println("OBJECT*************")
            println(it)
            println("JSON***************")
            println(Json.encodeToString(it))
        }
    }

    test("lag Json til OS 1"){
        val typeToken = object : TypeToken<List<Trekkpaalegg>>() {}.type

        val trekkpaalegg = trekkTable()
        val perioder = perioder()
        val osdok = trekkpaalegg.toTrekkDokument(perioder)
        println("OSDOK")
        println(osdok)
        println("OSDOK-JSON")
        println(Gson().toJson(osdok))
    }

    test("Sjekk reponse body"){
        val typeToken = object : TypeToken<List<Trekkpaalegg>>() {}.type
        val trekkpaalegg: List<Trekkpaalegg> = Gson().fromJson<List<Trekkpaalegg>>(bodyFraSkatt.toString(), typeToken)
        //println(Gson().fromJson(trekkJson, TrekkTilOppdrag.javaClass))

    }



})
val trekkJson =
    """
        {"dokument":
        	{"transaksjonsId":"ABCDEFGHIJKLMN",	
        	 "innrapporteringTrekk":
        		{"aksjonskode":"NY",							
        		 "navTrekkId": "0013209905",		
        		 "kreditorIdTss":"80000000000",		
        		 "kreditorTrekkId":"ABCDEFGHIJKLMN",	
        		 "debitorId":"01048012345",		
        		 "kodeTrekktype":"FRIS",	
        		 "kodeTrekkAlternativ":"LOPM",	
        		 "kid":"1234567890",					
        		 "kreditorsRef":"ABCDEFGHIJKLMN",		
        		 "kilde":"ABCDEFGHIJK"			
        		 "saldo":5000,					
        		 "prioritetFomDato":"YYYY-MM-DD",			
        		 "gyldigTomDato":"YYYY-MM-DD",				
        		 "perioder":
        			{"periode":					
        				[
        				 {"periodeFomDato":"YYYY-MM-DD",	
        				  "periodeTomDato":"YYYY-MM-DD",	
        				  "sats":2000				
        				 }
        				]
        			}
        		}
        	}
        }
        """.trimIndent()

val bodyFraSkatt = """
        [{"trekkid":"1","trekkversjon":1,"sekvensnummer":1,"opprettet":"2024-06-16T13:33:05.672Z","saksnummer":"sak-2023-899","trekkpliktig":"889640782","skyldner":"19628198007","trekkstatus":"aktiv","trekkstoerrelseForPeriode":[{"startdato":"2023-06-13","sluttdato":"2024-11-30","trekkbeloep":{"trekkbeloep":5000.0}},{"startdato":"2024-12-01","sluttdato":"2024-12-31","trekkbeloep":{"trekkbeloep":0.0}},{"startdato":"2025-01-01","trekkbeloep":{"trekkbeloep":5000.0}}],"betalingsinformasjon":{"betalingsmottaker":"971648198","kidnummer":"17654202404","kontonummer":"76940512057"}},{"trekkid":"2_xx","trekkversjon":1,"sekvensnummer":2,"opprettet":"2024-06-16T14:33:05.672Z","saksnummer":"sak-2023-900","trekkpliktig":"889640782","skyldner":"11656296129","trekkstatus":"aktiv","trekkstoerrelseForPeriode":[{"startdato":"2023-06-13","sluttdato":"2024-11-30","trekkbeloep":{"trekkbeloep":800.5}}],"betalingsinformasjon":{"betalingsmottaker":"971648198","kidnummer":"45645202404","kontonummer":"76940512057"}}]
    """.trimIndent()

fun trekkTable():TrekkpaleggTable =
    TrekkpaleggTable(
        trekkpaleggTableId = 1,
        trekkidNav = "",
        trekkidSke = "SKEID",
        trekkversjon = 2,
        sekvensnummer = 3,
        saksnummer = "sak01",
        opprettetSke = "2024-05-20",
        trekkpliktig = "987654321",
        skyldner = "12345678901",
        trekkstatus = "active",
        status = "MOTTATT",
        kid = "dette er kid",
        kontonummer = "12341212345",
        betalingsmottaker = "987654322",
        corrid = "dette_er_corrid",
        tidspunktSisteStatus = LocalDateTime.now(),
        tidspunktSendtOs = null,
        tidspunktOpprettet = LocalDateTime.now()
    )

fun perioder():List<TrekkPeriodeTable>  = listOf(
        TrekkPeriodeTable(
            trekkPeriodeTableId = 1,
            sekvensnummer = 3,
            trekkidSke = "SKEID",
            trekkversjon = 2,
            datoStart = "2025-01-01",
            datoSlutt = "2025-02-28",
            trekkbelop = 2000.00,
            trekkprosent = 0.0
        ),
        TrekkPeriodeTable(
            trekkPeriodeTableId = 1,
            sekvensnummer = 3,
            trekkidSke = "SKEID",
            trekkversjon = 2,
            datoStart = "2025-03-01",
            datoSlutt = "2025-04-30",
            trekkbelop = 0.0,
            trekkprosent = 15.0
        ),
        TrekkPeriodeTable(
            trekkPeriodeTableId = 1,
            sekvensnummer = 3,
            trekkidSke = "SKEID",
            trekkversjon = 2,
            datoStart = "2025-05-01",
            datoSlutt = "2025-05-31",
            trekkbelop = 2000.00,
            trekkprosent = 0.0
        )
    )

