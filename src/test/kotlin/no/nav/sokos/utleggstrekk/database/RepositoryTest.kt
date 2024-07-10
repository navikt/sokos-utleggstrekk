package no.nav.sokos.utleggstrekk.database

import io.kotest.core.spec.style.BehaviorSpec
import no.nav.sokos.utleggstrekk.util.TestContainer

internal class RepositoryTest : BehaviorSpec({

    Given("Vi har mottatt utleggstrekk...  ") {
        then("insert i database") {
            val ds = TestContainer(this::testCase.name).startContainer()
        }
    }
})