package no.nav.sokos.utleggstrekk.utils

import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.serialization.JsonConvertException
import mu.KotlinLogging

import no.nav.sokos.utleggstrekk.database.model.TrekkPeriodeTable
import no.nav.sokos.utleggstrekk.database.model.UtleggstrekkTable
import no.nav.sokos.utleggstrekk.domene.nav.Aksjonskode
import no.nav.sokos.utleggstrekk.domene.nav.Document
import no.nav.sokos.utleggstrekk.domene.nav.InnrapporteringTrekk
import no.nav.sokos.utleggstrekk.domene.nav.Periode
import no.nav.sokos.utleggstrekk.domene.nav.Perioder
import no.nav.sokos.utleggstrekk.domene.nav.TrekkAlternativ
import no.nav.sokos.utleggstrekk.domene.nav.TrekkTilOppdrag
import no.nav.sokos.utleggstrekk.domene.ske.Trekkpaalegg

private val logger = KotlinLogging.logger { }

fun TrekkPeriodeTable.toTrekkDokumentPeriode() =
    Periode(
        periodeFomDato = this.datoStart,
        periodeTomDato = this.datoSlutt,
        sats = this.sats,
    )

// TODO: skal dette være her?
fun UtleggstrekkTable.toTrekkDokument(
    periodeTableList: List<TrekkPeriodeTable>,
    aksjonskode: Aksjonskode = Aksjonskode.getAksjonskodeForTrekk(this),
    trekkAlternativ: TrekkAlternativ = periodeTableList[0].trekkAlternativ,
): TrekkTilOppdrag =
    TrekkTilOppdrag(
        dokument =
            Document(
                transaksjonsId = corrid,
                innrapporteringTrekk =
                    InnrapporteringTrekk(
                        aksjonskode = aksjonskode,
                        kreditorIdTss = betalingsmottaker,
                        kreditorTrekkId = "${trekkidSke}${trekkAlternativ.name[3]}",
                        debitorId = skyldner,
                        kodeTrekkAlternativ = trekkAlternativ,
                        kid = kid,
                        kreditorsRef = saksnummer,
                        kilde = "SOKOSUTLEGG",
                        saldo = 0.0,
                        prioritetFomDato = "${opprettetSke.year}-${opprettetSke.month.toString().padStart(
                            2,
                            '0',
                        )}-${opprettetSke.day.toString().padStart(2,'0')}",
                        perioder =
                            Perioder(
                                periode =
                                    periodeTableList.map {
                                        it.toTrekkDokumentPeriode()
                                    },
                            ),
                    ),
            ),
    )

suspend fun HttpResponse.toTrekkpaalegg() =
    try {
        body<List<Trekkpaalegg>>()
    } catch (e: JsonConvertException) {
        logger.error { "Feil i konvertering av response: ${e.message}" }
        emptyList()
    }
