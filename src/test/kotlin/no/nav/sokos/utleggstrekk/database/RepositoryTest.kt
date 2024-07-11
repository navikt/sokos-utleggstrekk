package no.nav.sokos.utleggstrekk.database

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.core.spec.style.FunSpec
import no.nav.sokos.utleggstrekk.util.TestContainer

internal class RepositoryTest : BehaviorSpec({

    Given("Vi har mottatt utleggstrekk...  ") {
        then("insert i database") {
            val ds = TestContainer(this::testCase.name).startContainer()
        }
    }

})

internal class KenTester : FunSpec({

    test("db timeformats"){
        val strDato = "2024-06-17T13:33:05.672Z"

    }

})