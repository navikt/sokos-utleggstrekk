@file:OptIn(ExperimentalTime::class)

package no.nav.sokos.utleggstrekk.service

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.just
import io.mockk.mockk

import no.nav.sokos.utleggstrekk.database.model.TrekkPeriodeTable
import no.nav.sokos.utleggstrekk.database.model.UtleggstrekkStatus.MOTTATT
import no.nav.sokos.utleggstrekk.database.model.UtleggstrekkTable
import no.nav.sokos.utleggstrekk.domene.nav.TrekkAlternativ.LOPM
import no.nav.sokos.utleggstrekk.domene.nav.TrekkAlternativ.LOPP
import no.nav.sokos.utleggstrekk.domene.nav.TrekkTilOppdrag
import no.nav.sokos.utleggstrekk.domene.ske.Trekkstatus.AKTIV
import no.nav.sokos.utleggstrekk.domene.ske.Trekkstatus.AVSLUTTET

class BehandleTrekkServiceTest :
    FunSpec(
        {
            val databaseServiceMock = mockk<DatabaseService>()
            val behandleTrekkService = BehandleTrekkService(databaseServiceMock)

            test("Når trekkdokument dannes skal datoer formatteres på yyyy-mm-dd format") {
                val testNr = 1
                val trekkITest = trekkTable1(testNr)
                val perioderiTest = periodetable1(testNr)
                coEvery { databaseServiceMock.hentAlleTrekkSomIkkeErSendt() } returns listOf(trekkITest)
                coEvery { databaseServiceMock.hentAllePerioderForTrekkVersjon(any<UtleggstrekkTable>()) } returns perioderiTest
                coEvery { databaseServiceMock.hentAllePerioderForTrekkId(any<UtleggstrekkTable>()) } returns perioderiTest
                coEvery { databaseServiceMock.lagreGenerertePerioder(any<List<TrekkPeriodeTable>>()) } just Runs
                val result = behandleTrekkService.lagTrekkSomSkalSendes()

                val prioritetFomDato =
                    result.values
                        .first()
                        .first()
                        .dokument.innrapporteringTrekk.prioritetFomDato

                prioritetFomDato shouldBe "2024-06-16"
            }

            test("Trekk med perioder kun med 1 trekkalternativ i trekkversjon 1 skal bli 1 NYTT trekk med 3 perioder") {
                val testNr = 1
                val trekkITest = trekkTable1(testNr)
                val perioderiTest = periodetable1(testNr)
                coEvery { databaseServiceMock.hentAlleTrekkSomIkkeErSendt() } returns listOf(trekkITest)
                coEvery { databaseServiceMock.hentAllePerioderForTrekkVersjon(any<UtleggstrekkTable>()) } returns perioderiTest
                coEvery { databaseServiceMock.hentAllePerioderForTrekkId(any<UtleggstrekkTable>()) } returns perioderiTest
                coEvery { databaseServiceMock.lagreGenerertePerioder(any<List<TrekkPeriodeTable>>()) } just Runs
                val result = behandleTrekkService.lagTrekkSomSkalSendes()

                result.size shouldBe 1
                result.keys.first() shouldBe trekkITest
                result.values.size shouldBe 1

                result.values
                    .first()
                    .first()
                    .dokument.innrapporteringTrekk.perioder.periode.size shouldBe 3
                result.values
                    .first()
                    .first()
                    .dokument.innrapporteringTrekk.aksjonskode.value shouldBe "NY"
            }
            test("Trekk med perioder kun med 1 trekkalternativ i trekkversjon 2 skal bli 1 ENDRET trekk med 3 perioder") {
                val testNr = 2
                val trekkITest = trekkTable1(testNr).copy(trekkversjon = 2)
                val perioderiTest = periodetable1(testNr).map { periode -> periode.copy(trekkversjon = 2) }
                coEvery { databaseServiceMock.hentAlleTrekkSomIkkeErSendt() } returns listOf(trekkITest)
                coEvery { databaseServiceMock.hentAllePerioderForTrekkVersjon(any<UtleggstrekkTable>()) } returns perioderiTest
                coEvery { databaseServiceMock.hentAllePerioderForTrekkId(any<UtleggstrekkTable>()) } returns periodetable1(testNr) + perioderiTest
                coEvery { databaseServiceMock.lagreGenerertePerioder(any<List<TrekkPeriodeTable>>()) } just Runs
                val result = behandleTrekkService.lagTrekkSomSkalSendes()

                result.size shouldBe 1
                result.keys.first() shouldBe trekkITest
                result.values.size shouldBe 1
                result.values
                    .first()
                    .first()
                    .dokument.innrapporteringTrekk.perioder.periode.size shouldBe 3
                result.values
                    .first()
                    .first()
                    .dokument.innrapporteringTrekk.aksjonskode.value shouldBe "ENDR"
            }
            test(
                "Trekk med perioder kun med 1 trekkalternativ i trekkversjon 2 hvor vi ikke har fått trekkversjon 1 skal bli 1 NYTT trekk med 3 perioder",
            ) {
                val testNr = 3
                val trekkITest = trekkTable1(testNr).copy(trekkversjon = 2)
                val perioderiTest = periodetable1(testNr).map { periode -> periode.copy(trekkversjon = 2) }
                coEvery { databaseServiceMock.hentAlleTrekkSomIkkeErSendt() } returns listOf(trekkITest)
                coEvery { databaseServiceMock.hentAllePerioderForTrekkVersjon(any<UtleggstrekkTable>()) } returns perioderiTest
                coEvery { databaseServiceMock.hentAllePerioderForTrekkId(any<UtleggstrekkTable>()) } returns perioderiTest
                coEvery { databaseServiceMock.lagreGenerertePerioder(any<List<TrekkPeriodeTable>>()) } just Runs
                val result = behandleTrekkService.lagTrekkSomSkalSendes()

                result.size shouldBe 1
                result.keys.first() shouldBe trekkITest
                result.values.size shouldBe 1
                result.values
                    .first()
                    .first()
                    .dokument.innrapporteringTrekk.perioder.periode.size shouldBe 3
                result.values
                    .first()
                    .first()
                    .dokument.innrapporteringTrekk.aksjonskode.value shouldBe "NY"
            }
            test("Trekk med 3 perioder med 2 trekkalternativ i trekkversjon 1 skal bli to trekk, begge NYE") {
                val testNr = 4
                val trekkITest = trekkTable1(testNr)
                val perioderiTest = periodetable1(testNr) + periodetable2(testNr)
                coEvery { databaseServiceMock.hentAlleTrekkSomIkkeErSendt() } returns listOf(trekkITest)
                coEvery { databaseServiceMock.hentAllePerioderForTrekkVersjon(any<UtleggstrekkTable>()) } returns perioderiTest
                coEvery { databaseServiceMock.hentAllePerioderForTrekkId(any<UtleggstrekkTable>()) } returns perioderiTest
                coEvery { databaseServiceMock.lagreGenerertePerioder(any<List<TrekkPeriodeTable>>()) } just Runs
                val result = behandleTrekkService.lagTrekkSomSkalSendes()

                result.values.first().size shouldBe 2
                result.values
                    .first()
                    .first()
                    .dokument.innrapporteringTrekk.perioder.periode.size shouldBe 6
                result.values
                    .first()
                    .first()
                    .dokument.innrapporteringTrekk.aksjonskode.value shouldBe "NY"
                result.values
                    .first()
                    .last()
                    .dokument.innrapporteringTrekk.perioder.periode.size shouldBe 6
                result.values
                    .first()
                    .last()
                    .dokument.innrapporteringTrekk.aksjonskode.value shouldBe "NY"

                val dok: List<TrekkTilOppdrag> = result.values.first()
                dok.forEach { t ->
                    println(" transaksjonsid: " + t.dokument.transaksjonsId)
                }
            }
            test(
                "Trekk med 3 perioder med 2 trekkalternativ, ett nytt i denne versjonen, i trekkversjon 2 skal bli to trekk, Ett NYTT og ett ENDRET",
            ) {
                val testNr = 5
                val trekkITest = trekkTable1(testNr).copy(trekkversjon = 2)
                val perioderiTest =
                    periodetable1(testNr).map { it.copy(trekkversjon = 2) } + periodetable2(testNr).map { it.copy(trekkversjon = 2) }
                coEvery { databaseServiceMock.hentAlleTrekkSomIkkeErSendt() } returns listOf(trekkITest)
                coEvery { databaseServiceMock.hentAllePerioderForTrekkVersjon(any<UtleggstrekkTable>()) } returns perioderiTest
                coEvery { databaseServiceMock.hentAllePerioderForTrekkId(any<UtleggstrekkTable>()) } returns periodetable1(testNr) + perioderiTest
                coEvery { databaseServiceMock.lagreGenerertePerioder(any<List<TrekkPeriodeTable>>()) } just Runs
                val result = behandleTrekkService.lagTrekkSomSkalSendes()

                result.values.first().size shouldBe 2
                result.values
                    .first()
                    .first()
                    .dokument.innrapporteringTrekk.perioder.periode.size shouldBe 6
                result.values
                    .first()
                    .first()
                    .dokument.innrapporteringTrekk.aksjonskode.value shouldBe "NY"
                result.values
                    .first()
                    .last()
                    .dokument.innrapporteringTrekk.perioder.periode.size shouldBe 6
                result.values
                    .first()
                    .last()
                    .dokument.innrapporteringTrekk.aksjonskode.value shouldBe "ENDR"
            }
            test("Trekk med 3 perioder med 1 nytt trekkalternativ i trekkversjon 2 skal bli to trekk, Ett NYTT og ett ENDRET") {
                val testNr = 5
                val trekkITest = trekkTable1(testNr).copy(trekkversjon = 2)
                val perioderiTest = periodetable2(testNr).map { it.copy(trekkversjon = 2) }
                coEvery { databaseServiceMock.hentAlleTrekkSomIkkeErSendt() } returns listOf(trekkITest)
                coEvery { databaseServiceMock.hentAllePerioderForTrekkVersjon(any<UtleggstrekkTable>()) } returns perioderiTest
                coEvery { databaseServiceMock.hentAllePerioderForTrekkId(any<UtleggstrekkTable>()) } returns periodetable1(testNr) + perioderiTest
                coEvery { databaseServiceMock.lagreGenerertePerioder(any<List<TrekkPeriodeTable>>()) } just Runs
                val result = behandleTrekkService.lagTrekkSomSkalSendes()

                result.values.first().size shouldBe 2
                result.values
                    .first()
                    .first()
                    .dokument.innrapporteringTrekk.perioder.periode.size shouldBe 3
                result.values
                    .first()
                    .first()
                    .dokument.innrapporteringTrekk.aksjonskode.value shouldBe "ENDR"
                result.values
                    .first()
                    .last()
                    .dokument.innrapporteringTrekk.perioder.periode.size shouldBe 3
                result.values
                    .first()
                    .last()
                    .dokument.innrapporteringTrekk.aksjonskode.value shouldBe "NY"
            }
            test("Trekk med trekkversjon uten perioder og kun ett trekkalternativ skal avsluttes, BLir kun ett avsluttet trekk") {
                val testNr = 6
                val trekkITest = trekkTable1(testNr).copy(trekkversjon = 2, trekkstatus = AVSLUTTET)
                coEvery { databaseServiceMock.hentAlleTrekkSomIkkeErSendt() } returns listOf(trekkITest)
                coEvery { databaseServiceMock.hentAllePerioderForTrekkVersjon(any<UtleggstrekkTable>()) } returns emptyList()
                coEvery { databaseServiceMock.hentAllePerioderForTrekkId(any<UtleggstrekkTable>()) } returns periodetable1(testNr)
                coEvery { databaseServiceMock.lagreGenerertePerioder(any<List<TrekkPeriodeTable>>()) } just Runs
                val result = behandleTrekkService.lagTrekkSomSkalSendes()

                result.values.size shouldBe 1
                result.values.first().size shouldBe 1
                result.values
                    .first()
                    .first()
                    .dokument.innrapporteringTrekk.aksjonskode.value shouldBe "OPPH"
                result.values
                    .first()
                    .first()
                    .dokument.innrapporteringTrekk.perioder.periode
                    .shouldBeEmpty()
            }
            test("Trekk med trekkversjon med kun ett trekkalternativ skal avsluttes, BLir kun ett avsluttet trekk med periodene") {
                val testNr = 7
                val trekkITest = trekkTable1(testNr).copy(trekkversjon = 2, trekkstatus = AVSLUTTET)
                coEvery { databaseServiceMock.hentAlleTrekkSomIkkeErSendt() } returns listOf(trekkITest)
                coEvery { databaseServiceMock.hentAllePerioderForTrekkVersjon(any<UtleggstrekkTable>()) } returns periodetable1(testNr)
                coEvery { databaseServiceMock.hentAllePerioderForTrekkId(any<UtleggstrekkTable>()) } returns periodetable1(testNr)
                coEvery { databaseServiceMock.lagreGenerertePerioder(any<List<TrekkPeriodeTable>>()) } just Runs
                val result = behandleTrekkService.lagTrekkSomSkalSendes()

                result.values.size shouldBe 1
                result.values.first().size shouldBe 1
                result.values
                    .first()
                    .first()
                    .dokument.innrapporteringTrekk.aksjonskode.value shouldBe "OPPH"
            }
            test(
                "Trekk med trekkversjon med to trekkalternativ skal avsluttes, BLir to avsluttet trekk forventer ikke perioder i avsluttet trekk",
            ) {
                val testNr = 8
                val trekkITest = trekkTable1(testNr).copy(trekkversjon = 2, trekkstatus = AVSLUTTET)
                coEvery { databaseServiceMock.hentAlleTrekkSomIkkeErSendt() } returns listOf(trekkITest)
                coEvery { databaseServiceMock.hentAllePerioderForTrekkVersjon(any<UtleggstrekkTable>()) } returns emptyList()
                coEvery { databaseServiceMock.hentAllePerioderForTrekkId(any<UtleggstrekkTable>()) } returns
                    periodetable1(testNr) + periodetable2(testNr)
                coEvery { databaseServiceMock.lagreGenerertePerioder(any<List<TrekkPeriodeTable>>()) } just Runs
                val result = behandleTrekkService.lagTrekkSomSkalSendes()

                result.size shouldBe 1
                result.values.first().size shouldBe 2
                result.values
                    .first()
                    .first()
                    .dokument.innrapporteringTrekk.aksjonskode.value shouldBe "OPPH"
                result.values
                    .first()
                    .last()
                    .dokument.innrapporteringTrekk.aksjonskode.value shouldBe "OPPH"
            }
        },
    )

private fun trekkTable1(testNr: Int) =
    UtleggstrekkTable(
        utleggstrekkTableId = 1,
        trekkidNavLOPP = null,
        trekkidNavLOPM = null,
        trekkidSke = "SKEID$testNr",
        trekkversjon = 1,
        sekvensnummer = 1,
        saksnummer = "sak01",
        opprettetSke = Instant.parse("2024-06-16T13:33:05.672Z").toLocalDateTime(TimeZone.currentSystemDefault()),
        trekkpliktig = "987654321",
        skyldner = "12345678901",
        trekkstatus = AKTIV,
        status = MOTTATT,
        kid = "12345654321",
        kontonummer = "12341212345",
        betalingsmottaker = "987654322",
        corrid = "dette_er_corrid_1",
        tidspunktSisteStatus = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
        tidspunktSendtOs = null,
        tidspunktOpprettet = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
    )

private fun periodetable1(testNr: Int) =
    listOf(
        TrekkPeriodeTable(
            trekkPeriodeTableId = 1,
            sekvensnummer = 1,
            trekkidSke = "SKEID$testNr",
            trekkversjon = 1,
            datoStart = "2025-01-01",
            datoSlutt = "2025-02-28",
            sats = 2000.00,
            trekkAlternativ = LOPM,
        ),
        TrekkPeriodeTable(
            trekkPeriodeTableId = 1,
            sekvensnummer = 1,
            trekkidSke = "SKEID$testNr",
            trekkversjon = 1,
            datoStart = "2025-03-01",
            datoSlutt = "2025-04-30",
            trekkAlternativ = LOPM,
            sats = 1000.00,
        ),
        TrekkPeriodeTable(
            trekkPeriodeTableId = 1,
            sekvensnummer = 1,
            trekkidSke = "SKEID$testNr",
            trekkversjon = 1,
            datoStart = "2025-05-01",
            datoSlutt = "2025-05-31",
            sats = 2500.00,
            trekkAlternativ = LOPM,
        ),
    )

private fun periodetable2(testNr: Int) =
    listOf(
        TrekkPeriodeTable(
            trekkPeriodeTableId = 1,
            sekvensnummer = 1,
            trekkidSke = "SKEID$testNr",
            trekkversjon = 1,
            datoStart = "2025-02-01",
            datoSlutt = "2025-02-28",
            sats = 20.00,
            trekkAlternativ = LOPP,
        ),
        TrekkPeriodeTable(
            trekkPeriodeTableId = 1,
            sekvensnummer = 1,
            trekkidSke = "SKEID$testNr",
            trekkversjon = 1,
            datoStart = "2025-04-01",
            datoSlutt = "2025-04-30",
            trekkAlternativ = LOPP,
            sats = 10.00,
        ),
        TrekkPeriodeTable(
            trekkPeriodeTableId = 1,
            sekvensnummer = 1,
            trekkidSke = "SKEID$testNr",
            trekkversjon = 1,
            datoStart = "2025-06-01",
            datoSlutt = "2025-06-31",
            sats = 25.00,
            trekkAlternativ = LOPP,
        ),
    )
