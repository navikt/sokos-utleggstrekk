package no.nav.sokos.utleggstrekk.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import io.mockk.mockk
import no.nav.sokos.utleggstrekk.client.SkeClient
import no.nav.sokos.utleggstrekk.security.maskinporten.MaskinportenAccessTokenClient
import no.nav.sokos.utleggstrekk.util.MockHttpClient
import no.nav.sokos.utleggstrekk.util.Responses

internal class UtleggstrekkServiceTest : FunSpec({

    test("Når SKE returnerer liste av utleggstrekk skal disse parses til objekter") {
        val mockClient = MockHttpClient().getClient(Responses.utleggsTrekkListe, HttpStatusCode.OK)
        val utleggsTrekkService = UtleggstrekkService(mockk<DatabaseService>(relaxed = true), SkeClient(mockClient, mockk<MaskinportenAccessTokenClient>(relaxed = true)))
        val utleggsTrekk = utleggsTrekkService.hentAlleNyeUtleggstrekk()

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
            trekkbeloep!!.trekkbeloep shouldBe 0
            trekkprosent!!.trekkprosent shouldBe 0
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
            trekkbeloep!!.trekkbeloep shouldBe 2.2
            trekkprosent!!.trekkprosent shouldBe 1
            kidnummer shouldBe "9981238184016280475641088"
            kontonummer shouldBe "12012012012"
        }
    }
})