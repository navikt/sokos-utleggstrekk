package sokos.utleggstrekk.service

import io.kotest.core.annotation.Ignored
import io.kotest.core.spec.style.FunSpec

@Ignored
class UtleggstrekkServiceTest : FunSpec({

//    test("Sjekk"){
//        val xml = getResourceAsText("/skattekort.xml")
//        println(xml)
//        val skattekort = Skattekort(
//            fnr = "01026900561",
//            inntektsar = "2023",
//            skattekort = if (xml == null) "" else xml
//        )
//        println(Json.encodeToString(Skattekort.serializer(), skattekort))
//    }
//
//    test("xml"){
//        val xml = getResourceAsText("/skattekort.xml")
//        val encodexml = xml!!.encodeToByteArray().encodeBase64().also {println("Encode: \n${it}") }
//        println("decodeXml: ${encodexml!!.encodeBase64()}")
//        val jsonSkattekort =  Json.encodeToString(Skattekort(
//            fnr = "01026900561",
//            inntektsar = "2023",
//            skattekort = encodexml
//        )).also { println("Json: \n $it") }
//
//        val decodeSkattekortData = Json.decodeFromString<Skattekort>(jsonSkattekort)
//        println("OrigEncodeSkattekort: \n${decodeSkattekortData.skattekort}")
//        val decodedReplacedXml = decodeSkattekortData.skattekort.decodeBase64String().replace(""""""".toRegex(), "'" ).also { println("DecodeSkattekort: \n$it") }
//
//    }
//
//    test("json"){
//        val json = getResourceAsText("/feilbody.json")
//        println(json)
//        val decodedSkattekort = Json.decodeFromString<Skattekort2>(json!!)
//        println("Skattekort.År= ${decodedSkattekort.inntektsar}")
//        println("Skattekort.fnr= ${decodedSkattekort.fnr}")
//        println("Skattekort.skattekort(B64)= ${decodedSkattekort.skattekort}")
//    }

})
