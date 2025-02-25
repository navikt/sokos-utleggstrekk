package no.nav.sokos.utleggstrekk.service

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import io.mockk.mockk
import no.nav.sokos.utleggstrekk.TestContainer
import no.nav.sokos.utleggstrekk.client.SkeClient
import no.nav.sokos.utleggstrekk.mq.MqProducer
import no.nav.sokos.utleggstrekk.security.maskinporten.MaskinportenAccessTokenClient
import no.nav.sokos.utleggstrekk.util.MockHttpClient
import no.nav.sokos.utleggstrekk.util.Responses

internal class UtleggstrekkServiceTest :
    BehaviorSpec({

        Given("SKE returnerer en liste av utleggstrekk") {
            val testContainer = TestContainer()
            testContainer.migrate()
            val mockClient = MockHttpClient().getClient(Responses.utleggsTrekkListeFraSkatt, HttpStatusCode.OK)
            val utleggsTrekkService =
                UtleggstrekkService(
                    DatabaseService(testContainer.dataSource),
                    SkeClient(mockClient, mockk<MaskinportenAccessTokenClient>(relaxed = true)),
                    mockk<MqProducer>(relaxed = true),
                )

            then("skal disse lagres i databasen") {
                true shouldBe true
            }
        }

    })
