import no.nav.sokos.utleggstrekk.database.model.MidlertidigStansTable
import no.nav.sokos.utleggstrekk.database.model.TrekkTable
import java.time.LocalDateTime

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
