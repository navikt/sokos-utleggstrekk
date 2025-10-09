package no.nav.sokos.utleggstrekk.utils

import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.serialization.JsonConvertException
import mu.KotlinLogging

import no.nav.sokos.utleggstrekk.database.model.TrekkPeriodeTable
import no.nav.sokos.utleggstrekk.domene.nav.Periode
import no.nav.sokos.utleggstrekk.domene.ske.Trekkpaalegg

private val logger = KotlinLogging.logger { }

fun TrekkPeriodeTable.toTrekkDokumentPeriode() =
    Periode(
        periodeFomDato = this.datoStart,
        periodeTomDato = this.datoSlutt,
        sats = this.sats,
    )

suspend fun HttpResponse.toTrekkpaalegg() =
    try {
        body<List<Trekkpaalegg>>()
    } catch (e: JsonConvertException) {
        logger.error { "Feil i konvertering av response: ${e.message}" }
        emptyList()
    }