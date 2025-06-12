package no.nav.sokos.utleggstrekk.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.just
import io.mockk.mockk
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import no.nav.sokos.utleggstrekk.database.model.TrekkPeriodeTable
import no.nav.sokos.utleggstrekk.database.model.UtleggstrekkTable

class BehandleTrekkServiceTest : FunSpec(
    {
        val databaseServiceMock = mockk<DatabaseService>()
        val behandleTrekkService = BehandleTrekkService(databaseServiceMock)

        test("Trekk med perioder kun med 1 trekkalternativ i trekkversjon 1 skal bli 1 NYTT trekk med 3 perioder") {
            val testNr = 1
            val trekkITest = trekkTable1(testNr)
            val perioderiTest = periodetable1(testNr)
            coEvery { databaseServiceMock.hentAlleTrekkSomIkkeErSendt() } returns listOf(trekkITest)
            coEvery { databaseServiceMock.hentAllePerioderForTrekkVersjon( any() as UtleggstrekkTable ) } returns  perioderiTest
            coEvery { databaseServiceMock.hentAllePerioderForTrekkId( any() as UtleggstrekkTable ) } returns  perioderiTest
            coEvery { databaseServiceMock.lagreGenerertePerioder( any() as List<TrekkPeriodeTable> ) } just Runs
            val result = behandleTrekkService.lagTrekkSomSkalSendes()
            println(result)

            result.size shouldBe 1
            result.keys.first() shouldBe trekkITest
            result.values.size shouldBe 1
            result.values.first().first().dokument.innrapporteringTrekk.perioder.periode.size shouldBe 3
            result.values.first().first().dokument.innrapporteringTrekk.aksjonskode.value shouldBe "NY"

        }
        test("Trekk med perioder kun med 1 trekkalternativ i trekkversjon 2 skal bli 1 ENDRET trekk med 3 perioder") {
            val testNr = 2
            val trekkITest = trekkTable1(testNr).copy(trekkversjon = 2)
            val perioderiTest = periodetable1(testNr).map { periode -> periode.copy(trekkversjon = 2) }
            coEvery { databaseServiceMock.hentAlleTrekkSomIkkeErSendt() } returns listOf(trekkITest)
            coEvery { databaseServiceMock.hentAllePerioderForTrekkVersjon( any() as UtleggstrekkTable ) } returns  perioderiTest
            coEvery { databaseServiceMock.hentAllePerioderForTrekkId( any() as UtleggstrekkTable ) } returns  periodetable1(testNr) + perioderiTest
            coEvery { databaseServiceMock.lagreGenerertePerioder( any() as List<TrekkPeriodeTable> ) } just Runs
            val result = behandleTrekkService.lagTrekkSomSkalSendes()
            println(result)

            result.size shouldBe 1
            result.keys.first() shouldBe trekkITest
            result.values.size shouldBe 1
            result.values.first().first().dokument.innrapporteringTrekk.perioder.periode.size shouldBe 3
            result.values.first().first().dokument.innrapporteringTrekk.aksjonskode.value shouldBe "ENDR"

        }
        test("Behandle nytt trekk med perioder kun med 1 trekkalternativ i trekkversjon 2 skal bli 1 NYTT trekk med 3 perioder") {
            val testNr = 3
            val trekkITest = trekkTable1(testNr).copy(trekkversjon = 2)
            val perioderiTest = periodetable1(testNr).map { periode -> periode.copy(trekkversjon = 2) }
            coEvery { databaseServiceMock.hentAlleTrekkSomIkkeErSendt() } returns listOf(trekkITest)
            coEvery { databaseServiceMock.hentAllePerioderForTrekkVersjon( any() as UtleggstrekkTable ) } returns  perioderiTest
            coEvery { databaseServiceMock.hentAllePerioderForTrekkId( any() as UtleggstrekkTable ) } returns  perioderiTest
            coEvery { databaseServiceMock.lagreGenerertePerioder( any() as List<TrekkPeriodeTable> ) } just Runs
            val result = behandleTrekkService.lagTrekkSomSkalSendes()
            println(result)

            result.size shouldBe 1
            result.keys.first() shouldBe trekkITest
            result.values.size shouldBe 1
            result.values.first().first().dokument.innrapporteringTrekk.perioder.periode.size shouldBe 3
            result.values.first().first().dokument.innrapporteringTrekk.aksjonskode.value shouldBe "NY"

        }
        test("Trekk med perioder med 2 trekkalternativ i trekkversjon 1 skal bli to trekk, begge NYE") {
            val testNr = 4
            val trekkITest = trekkTable1(testNr)
            val perioderiTest = periodetable1(testNr) + periodetable2(testNr)
            coEvery { databaseServiceMock.hentAlleTrekkSomIkkeErSendt() } returns listOf(trekkITest)
            coEvery { databaseServiceMock.hentAllePerioderForTrekkVersjon( any() as UtleggstrekkTable ) } returns  perioderiTest
            coEvery { databaseServiceMock.hentAllePerioderForTrekkId( any() as UtleggstrekkTable ) } returns  perioderiTest
            coEvery { databaseServiceMock.lagreGenerertePerioder( any() as List<TrekkPeriodeTable> ) } just Runs
            val result = behandleTrekkService.lagTrekkSomSkalSendes()
            println(result)

            result.values.first().size shouldBe 2
            result.values.first().first().dokument.innrapporteringTrekk.perioder.periode.size shouldBe 6
            result.values.first().first().dokument.innrapporteringTrekk.aksjonskode.value shouldBe "NY"
            result.values.first().last().dokument.innrapporteringTrekk.perioder.periode.size shouldBe 6
            result.values.first().last().dokument.innrapporteringTrekk.aksjonskode.value shouldBe "NY"
        }
        test("Trekk med perioder med 2 trekkalternativ i trekkversjon 2 skal bli to trekk, Ett NYTT og ett ENDRET") {
            val testNr = 5
            val trekkITest = trekkTable1(testNr).copy(trekkversjon = 2)
            val perioderiTest = periodetable1(testNr).map{ it.copy(trekkversjon=2) } + periodetable2(testNr).map { it.copy(trekkversjon = 2) }
            coEvery { databaseServiceMock.hentAlleTrekkSomIkkeErSendt() } returns listOf(trekkITest)
            coEvery { databaseServiceMock.hentAllePerioderForTrekkVersjon( any() as UtleggstrekkTable ) } returns  perioderiTest
            coEvery { databaseServiceMock.hentAllePerioderForTrekkId( any() as UtleggstrekkTable ) } returns  periodetable1(testNr) + perioderiTest
            coEvery { databaseServiceMock.lagreGenerertePerioder( any() as List<TrekkPeriodeTable> ) } just Runs
            val result = behandleTrekkService.lagTrekkSomSkalSendes()
            println(result)

            result.values.first().size shouldBe 2
            result.values.first().first().dokument.innrapporteringTrekk.perioder.periode.size shouldBe 6
            result.values.first().first().dokument.innrapporteringTrekk.aksjonskode.value shouldBe "NY"
            result.values.first().last().dokument.innrapporteringTrekk.perioder.periode.size shouldBe 6
            result.values.first().last().dokument.innrapporteringTrekk.aksjonskode.value shouldBe "ENDR"
        }
    }
)


private fun  trekkTable1(testNr: Int) = UtleggstrekkTable(
            utleggstrekkTableId = 1,
            trekkidNav = null,
            trekkidSke = "SKEID${testNr}",
            trekkversjon = 1,
            sekvensnummer = 1,
            saksnummer = "sak01",
            opprettetSke = Instant.parse("2024-06-16T13:33:05.672Z").toLocalDateTime(TimeZone.currentSystemDefault()),
            trekkpliktig = "987654321",
            skyldner = "12345678901",
            trekkstatus = "aktive",
            status = "MOTTATT",
            kid = "12345654321",
            kontonummer = "12341212345",
            betalingsmottaker = "987654322",
            corrid = "dette_er_corrid_1",
            tidspunktSisteStatus = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
            tidspunktSendtOs = null,
            tidspunktOpprettet = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
        )

private fun periodetable1(testNr:Int) = listOf(
            TrekkPeriodeTable(
                trekkPeriodeTableId = 1,
                sekvensnummer = 1,
                trekkidSke = "SKEID${testNr}",
                trekkversjon = 1,
                datoStart = "2025-01-01",
                datoSlutt = "2025-02-28",
                sats = 2000.00,
                trekkAlternativ = "LOPM",
            ),
            TrekkPeriodeTable(
                trekkPeriodeTableId = 1,
                sekvensnummer = 1,
                trekkidSke = "SKEID${testNr}",
                trekkversjon = 1,
                datoStart = "2025-03-01",
                datoSlutt = "2025-04-30",
                trekkAlternativ = "LOPM",
                sats = 1000.00,
            ),
            TrekkPeriodeTable(
                trekkPeriodeTableId = 1,
                sekvensnummer = 1,
                trekkidSke = "SKEID${testNr}",
                trekkversjon = 1,
                datoStart = "2025-05-01",
                datoSlutt = "2025-05-31",
                sats = 2500.00,
                trekkAlternativ = "LOPM",

                )
    )
private fun periodetable2(testNr: Int) = listOf(
            TrekkPeriodeTable(
                trekkPeriodeTableId = 1,
                sekvensnummer = 1,
                trekkidSke = "SKEID${testNr}",
                trekkversjon = 1,
                datoStart = "2025-02-01",
                datoSlutt = "2025-02-28",
                sats = 20.00,
                trekkAlternativ = "LOPP",
            ),
            TrekkPeriodeTable(
                trekkPeriodeTableId = 1,
                sekvensnummer = 1,
                trekkidSke = "SKEID${testNr}",
                trekkversjon = 1,
                datoStart = "2025-04-01",
                datoSlutt = "2025-04-30",
                trekkAlternativ = "LOPP",
                sats = 10.00,
            ),
            TrekkPeriodeTable(
                trekkPeriodeTableId = 1,
                sekvensnummer = 1,
                trekkidSke = "SKEID${testNr}",
                trekkversjon = 1,
                datoStart = "2025-06-01",
                datoSlutt = "2025-06-31",
                sats = 25.00,
                trekkAlternativ = "LOPP",

                )
    )
