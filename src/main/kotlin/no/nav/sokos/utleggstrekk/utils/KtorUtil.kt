package no.nav.sokos.utleggstrekk.utils

import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode

fun HttpResponse.isSuccessful() = status.value in 200..299

fun HttpStatusCode.isClientError() = value in 400..499

fun HttpStatusCode.isServerError() = value in 500..599