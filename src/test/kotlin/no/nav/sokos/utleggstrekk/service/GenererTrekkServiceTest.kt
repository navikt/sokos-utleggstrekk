package no.nav.sokos.utleggstrekk.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import no.nav.sokos.utleggstrekk.database.model.MidlertidigStansTable
import no.nav.sokos.utleggstrekk.database.model.TrekkTable
import java.time.LocalDateTime

class GenererTrekkServiceTest : FunSpec({

    test("når et av to trekk fra SKE har 3 midlertidig  stans skal det genereres 4 trekk for dette trekk og et trekk for det uten midlertidig stans") {
        val trekkliste = testTrekkTableList
        val stansListe1 = testMidlertidigStansTable1
        val stansListe2 =  testMidlertidigStansTable2
        val  dbseriveMock  = mockk<DatabaseService>()
        val generertrekkService = GenererTrekkService(dbseriveMock)
        every { dbseriveMock.hentMidletidigStansForSekvensnr(1) }  returns stansListe1
        every { dbseriveMock.hentMidletidigStansForSekvensnr(2) } returns stansListe2

        val genererteTrekk = generertrekkService.lagTrekkTilOsFraMidlertidigeTrekk(trekkliste)

        stansListe1.size shouldBe 3
        stansListe1.get(0).trekksekvensnr shouldBe 1
        stansListe1.get(0).startPeriode shouldBe "2024-12"
        stansListe1.get(0).sluttPeriode shouldBe "2024-12"
        stansListe1.get(1).trekksekvensnr shouldBe 1
        stansListe1.get(1).startPeriode shouldBe "2025-06"
        stansListe1.get(1).sluttPeriode shouldBe "2025-07"
        stansListe1.get(2).trekksekvensnr shouldBe 1
        stansListe1.get(2).startPeriode shouldBe "2026-01"
        stansListe1.get(2).sluttPeriode shouldBe "2026-01"

        genererteTrekk.size shouldBe 5
        genererteTrekk.get(0).sekvensnr shouldBe 1
        genererteTrekk.get(0).startPeriode shouldBe "2024-01-01"
        genererteTrekk.get(0).sluttPeriode shouldBe "2024-11-30"
        genererteTrekk.get(1).sekvensnr shouldBe 1
        genererteTrekk.get(1).startPeriode shouldBe "2025-01-01"
        genererteTrekk.get(1).sluttPeriode shouldBe "2025-05-31"
        genererteTrekk.get(2).sekvensnr shouldBe 1
        genererteTrekk.get(2).startPeriode shouldBe "2025-08-01"
        genererteTrekk.get(2).sluttPeriode shouldBe "2025-12-31"
        genererteTrekk.get(3).sekvensnr shouldBe 1
        genererteTrekk.get(3).startPeriode shouldBe "2026-02-01"
        genererteTrekk.get(3).sluttPeriode shouldBe "2026-04-30"
        genererteTrekk.get(4).sekvensnr shouldBe 2
        genererteTrekk.get(4).startPeriode shouldBe "2024-01-01"
        genererteTrekk.get(4).sluttPeriode shouldBe "2026-04-30"

    }
})


val testTrekkTableList = listOf(
    TrekkTable(
        trekktableid = 1,
        trekkid = "1",
        sekvensnr = 1,
        trekkversjon = 1,
        trekkopprettet = LocalDateTime.of(2023, 12, 1, 12, 0),
        trekkpliktig = "987654321",
        skyldner = "12345678901",
        trekkstatus = "AKTIV",
        startPeriode = "2024-01",
        sluttPeriode = "2026-04",
        trekkbelop = 1000.0,
        kid = "12345678901234567890",
        kontonummer = "12341212345",
        corrid = "corrID_1A",
        status = "MOTTATT",
        tidspunktMottatt = LocalDateTime.of(2023, 12, 1, 12, 0),
        tidspunktSisteStatus = LocalDateTime.of(2023, 12, 1, 12, 0),
        tidspunktOpprettet = LocalDateTime.of(2023, 12, 1, 12, 0),

        ), TrekkTable(
        trekktableid = 2,
        trekkid = "2",
        sekvensnr = 2,
        trekkversjon = 1,
        trekkopprettet = LocalDateTime.of(2023, 12, 1, 12, 0),
        trekkpliktig = "987654322",
        skyldner = "12345678902",
        trekkstatus = "AKTIV",
        startPeriode = "2024-01",
        sluttPeriode = "2026-04",
        trekkbelop = 1000.0,
        kid = "12345678901234567892",
        kontonummer = "12341212342",
        corrid = "corrID_2A",
        status = "MOTTATT",
        tidspunktMottatt = LocalDateTime.of(2023, 12, 1, 12, 0),
        tidspunktSisteStatus = LocalDateTime.of(2023, 12, 1, 12, 0),
        tidspunktOpprettet = LocalDateTime.of(2023, 12, 1, 12, 0),

        )
)

val testMidlertidigStansTable1 = listOf(
    MidlertidigStansTable(
        midlertidigstansid = 1,
        trekksekvensnr = 1,
        startPeriode = "2024-12",
        sluttPeriode = "2024-12"
    ),
    MidlertidigStansTable(
        midlertidigstansid = 1,
        trekksekvensnr = 1,
        startPeriode = "2025-06",
        sluttPeriode = "2025-07"
    ),
    MidlertidigStansTable(
        midlertidigstansid = 1,
        trekksekvensnr = 1,
        startPeriode = "2026-01",
        sluttPeriode = "2026-01"
    ),
)

val testMidlertidigStansTable2 = emptyList<MidlertidigStansTable>()
