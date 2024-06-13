import io.kotest.core.annotation.Ignored
import io.kotest.core.spec.style.FunSpec
import kotlinx.serialization.json.Json
import sokos.utleggstrekk.models.Utleggstrekk

@Ignored
class Test : FunSpec({

    test("test") {
        val s = getResourceAsText("testfil.json")
        println(s)
        val u =Json.decodeFromString<List<Utleggstrekk>>(s!!)
        println(u)
    }

    test("xml"){

    }


})
