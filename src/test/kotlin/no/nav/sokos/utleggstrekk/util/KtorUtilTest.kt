package no.nav.sokos.utleggstrekk.util

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.statement.HttpResponse
import io.mockk.every
import io.mockk.mockk

import no.nav.sokos.utleggstrekk.utils.isClientError
import no.nav.sokos.utleggstrekk.utils.isServerError
import no.nav.sokos.utleggstrekk.utils.isSuccessful

class KtorUtilTest :
    FunSpec({
        test("isSuccessful skal returnere `true` hvis status koden er i 200s og `false` ellers") {
            val response =
                mockk<HttpResponse> {
                    every { status.value } returns 200 andThen 299 andThen 199 andThen 300
                }

            response.isSuccessful() shouldBe true
            response.isSuccessful() shouldBe true
            response.isSuccessful() shouldBe false
            response.isSuccessful() shouldBe false
        }

        test("isClientError skal returnere `true` hvis status koden er i 400s og `false` ellers") {
            val response =
                mockk<HttpResponse> {
                    every { status.value } returns 400 andThen 499 andThen 399 andThen 500
                }

            response.status.isClientError() shouldBe true
            response.status.isClientError() shouldBe true
            response.status.isClientError() shouldBe false
            response.status.isClientError() shouldBe false
        }

        test("isServerError skal returnere `true` hvis status koden er i 500s og `false` ellers ") {
            val response =
                mockk<HttpResponse> {
                    every { status.value } returns 500 andThen 599 andThen 499
                }

            response.status.isServerError() shouldBe true
            response.status.isServerError() shouldBe true
            response.status.isServerError() shouldBe false
        }
    })