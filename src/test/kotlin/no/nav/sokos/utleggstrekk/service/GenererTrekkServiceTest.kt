package no.nav.sokos.utleggstrekk.service

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import no.nav.sokos.utleggstrekk.database.model.MidlertidigStansTable
import no.nav.sokos.utleggstrekk.database.model.TrekkTable
import java.time.LocalDateTime

internal class GenererTrekkServiceTest :
    BehaviorSpec({

        Given("Vi mottar tre trekk fra SKE") {
            val dbserviceMock = mockk<DatabaseService>()
            every { dbserviceMock.hentMidletidigStansForSekvensnr(1) } returns testMidlertidigStansTable1
            every { dbserviceMock.hentMidletidigStansForSekvensnr(2) } returns emptyList()
            every { dbserviceMock.hentMidletidigStansForSekvensnr(3) } returns emptyList()

            `when`("Ett trekk har 3 midlertidig stans") {
                val genererteTrekk = GenererTrekkService(dbserviceMock).lagTrekkTilOs(testTrekkTableList)

                then("skal det genereres 4 trekk for det") {
                    genererteTrekk.size shouldBe 6
                    genererteTrekk[0].sekvensnr shouldBe 1
                    genererteTrekk[0].startPeriode shouldBe "2024-01-01"
                    genererteTrekk[0].sluttPeriode shouldBe "2024-11-30"
                    genererteTrekk[1].sekvensnr shouldBe 1
                    genererteTrekk[1].startPeriode shouldBe "2025-01-01"
                    genererteTrekk[1].sluttPeriode shouldBe "2025-05-31"
                    genererteTrekk[2].sekvensnr shouldBe 1
                    genererteTrekk[2].startPeriode shouldBe "2025-08-01"
                    genererteTrekk[2].sluttPeriode shouldBe "2025-12-31"
                    genererteTrekk[3].sekvensnr shouldBe 1
                    genererteTrekk[3].startPeriode shouldBe "2026-02-01"
                    genererteTrekk[3].sluttPeriode shouldBe "2026-04-30"
                }
                and("De andre trekkene er vanlig") {
                    then("skal det genereres 1 trekk for hver") {
                        genererteTrekk[4].sekvensnr shouldBe 2
                        genererteTrekk[4].startPeriode shouldBe "2024-01-01"
                        genererteTrekk[4].sluttPeriode shouldBe "2026-04-30"
                        genererteTrekk[5].sekvensnr shouldBe 3
                    }
                }
            }
        }
    })

val testTrekkTableList =
    listOf(
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
        ),
        TrekkTable(
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
        ),
        TrekkTable(
            trekktableid = 3,
            trekkid = "3",
            sekvensnr = 3,
            trekkversjon = 1,
            trekkopprettet = LocalDateTime.of(2023, 12, 1, 12, 0),
            trekkpliktig = "987654322",
            skyldner = "12345678902",
            trekkstatus = "AKTIV",
            startPeriode = "2024-01",
            sluttPeriode = "2026-04",
            trekkprosent = 15.0,
            kid = "12345678901234567892",
            kontonummer = "12341212342",
            corrid = "corrID_EA",
            status = "MOTTATT",
            tidspunktMottatt = LocalDateTime.of(2023, 12, 1, 12, 0),
            tidspunktSisteStatus = LocalDateTime.of(2023, 12, 1, 12, 0),
            tidspunktOpprettet = LocalDateTime.of(2023, 12, 1, 12, 0),
        ),
    )

val testMidlertidigStansTable1 =
    listOf(
        MidlertidigStansTable(
            midlertidigstansid = 1,
            trekksekvensnr = 1,
            startPeriode = "2024-12",
            sluttPeriode = "2024-12",
        ),
        MidlertidigStansTable(
            midlertidigstansid = 1,
            trekksekvensnr = 1,
            startPeriode = "2025-06",
            sluttPeriode = "2025-07",
        ),
        MidlertidigStansTable(
            midlertidigstansid = 1,
            trekksekvensnr = 1,
            startPeriode = "2026-01",
            sluttPeriode = "2026-01",
        ),
    )