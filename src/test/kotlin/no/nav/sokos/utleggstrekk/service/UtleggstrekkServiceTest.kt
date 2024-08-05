package no.nav.sokos.utleggstrekk.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import io.mockk.justRun
import io.mockk.mockk
import no.nav.sokos.utleggstrekk.client.SkeClient
import no.nav.sokos.utleggstrekk.mq.MqProducer
import no.nav.sokos.utleggstrekk.security.maskinporten.MaskinportenAccessTokenClient
import no.nav.sokos.utleggstrekk.util.MockHttpClient
import no.nav.sokos.utleggstrekk.util.Responses

internal class UtleggstrekkServiceTest : FunSpec({

    test("Når SKE returnerer liste av utleggstrekk skal disse parses til objekter").config(enabled = false) {
        val mockClient = MockHttpClient().getClient(Responses.utleggsTrekkListe, HttpStatusCode.OK)
        val mqProducerMock = mockk<MqProducer>()
        val genererTrekkServiceMock = mockk<GenererTrekkService>()
        val utleggsTrekkService = UtleggstrekkService(mockk<DatabaseService>(relaxed = true),genererTrekkServiceMock, SkeClient(mockClient, mockk<MaskinportenAccessTokenClient>(relaxed = true)), mqProducerMock)
        justRun { mqProducerMock.send(any()) }
        justRun { mqProducerMock.commit() }

        val utleggsTrekk = utleggsTrekkService.behandleUtleggstrekk()

        utleggsTrekk.size shouldBe 2
        with(utleggsTrekk.first()) {
            trekkid shouldBe "1"
            trekkversjon shouldBe 1
            sekvensnummer shouldBe 1
            opprettet shouldBe "2024-07-10T08:24:34.143Z"
            trekkpliktig shouldBe "123456789"
            skyldner shouldBe "22003648649"
            trekkstatus shouldBe "aktiv"
            startPeriode shouldBe "2024-12"
            sluttPeriode shouldBe "2024-12"
            midlertidigStans!!.size shouldBe 1
            with(midlertidigStans!!.first()) {
                startPeriode shouldBe "2024-12"
                sluttPeriode shouldBe "2024-12"
            }
            trekkbeloep!!.trekkbeloep shouldBe 1000
            trekkprosent shouldBe null
            kidnummer shouldBe "8981238184016280475641088"
            kontonummer shouldBe "19019019019"
        }

        with(utleggsTrekk.last()) {
            trekkid shouldBe "2"
            trekkversjon shouldBe 1
            sekvensnummer shouldBe 1
            opprettet shouldBe "2024-07-10T08:24:34.143Z"
            trekkpliktig shouldBe "987654321"
            skyldner shouldBe "22003648649"
            trekkstatus shouldBe "inaktiv"
            startPeriode shouldBe "2024-11"
            sluttPeriode shouldBe "2024-11"
            midlertidigStans!!.size shouldBe 1
            with(midlertidigStans!!.first()) {
                startPeriode shouldBe "2024-11"
                sluttPeriode shouldBe "2024-11"
            }
            trekkbeloep shouldBe null
            trekkprosent!!.trekkprosent shouldBe 10.0
            kidnummer shouldBe "9981238184016280475641088"
            kontonummer shouldBe "12012012012"
        }
    }
})