package no.nav.sokos.utleggstrekk

import com.google.gson.Gson
import io.kotest.core.spec.style.FunSpec
import no.nav.sokos.utleggstrekk.domene.ske.Trekkpaalegg
import org.testcontainers.shaded.com.google.common.reflect.TypeToken

internal class KenTesterJson : FunSpec({

    test("db timeformats") {
        val strDato = "2024-06-17T13:33:05.672Z"
    }

    val bodyFraSkatt = """
        [{"trekkid":"1","trekkversjon":1,"sekvensnummer":1,"opprettet":"2024-06-16T13:33:05.672Z","saksnummer":"sak-2023-899","trekkpliktig":"889640782","skyldner":"19628198007","trekkstatus":"aktiv","trekkstoerrelseForPeriode":[{"startdato":"2023-06-13","sluttdato":"2024-11-30","trekkbeloep":{"trekkbeloep":5000.0}},{"startdato":"2024-12-01","sluttdato":"2024-12-31","trekkbeloep":{"trekkbeloep":0.0}},{"startdato":"2025-01-01","trekkbeloep":{"trekkbeloep":5000.0}}],"betalingsinformasjon":{"betalingsmottaker":"971648198","kidnummer":"17654202404","kontonummer":"76940512057"}},{"trekkid":"2_xx","trekkversjon":1,"sekvensnummer":2,"opprettet":"2024-06-16T14:33:05.672Z","saksnummer":"sak-2023-900","trekkpliktig":"889640782","skyldner":"11656296129","trekkstatus":"aktiv","trekkstoerrelseForPeriode":[{"startdato":"2023-06-13","sluttdato":"2024-11-30","trekkbeloep":{"trekkbeloep":800.5}}],"betalingsinformasjon":{"betalingsmottaker":"971648198","kidnummer":"45645202404","kontonummer":"76940512057"}}]
    """.trimIndent()

    test("parsing av reposnse fra skatt") {
        println(bodyFraSkatt)
        val typeToken = object : TypeToken<List<Trekkpaalegg>>() {}.type
        val trekkpaalegg: List<Trekkpaalegg> = Gson().fromJson<List<Trekkpaalegg>>(bodyFraSkatt.toString(), typeToken)
        println("antall: ${trekkpaalegg.size}")
        trekkpaalegg.forEach {
//            println("TrekkID: ${it.trekkid}")
//            println("TrekkVersjon${it.trekkversjon}")
//            println("fnr: ${it.skyldner}")
            println(it)
        }
    }

    test("lag Json til OS"){
        val typeToken = object : TypeToken<List<Trekkpaalegg>>() {}.type
        val trekkpaalegg: List<Trekkpaalegg> = Gson().fromJson<List<Trekkpaalegg>>(bodyFraSkatt.toString(), typeToken)
        val osDokument = trekkpaalegg.map {
            //it.toTrekkDokument()
        }
        println(trekkpaalegg.forEach { println(it) })
        println(osDokument.forEach { println(it) })
        println(Gson().toJson(osDokument))
    }


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
})

