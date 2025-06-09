package no.nav.sokos.utleggstrekk

import io.kotest.core.spec.style.FunSpec
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import no.nav.sokos.utleggstrekk.database.model.TrekkPeriodeTable
import no.nav.sokos.utleggstrekk.database.model.UtleggstrekkTable

//TODO  denne skal slettes, brukes kun for å sjekke småting direkte

internal class KenTesterJson : FunSpec({




})


fun trekkTable():UtleggstrekkTable =
    UtleggstrekkTable(
        utleggstrekkTableId = 1,
        trekkidNav = "",
        trekkidSke = "SKEID",
        trekkversjon = 2,
        sekvensnummer = 3,
        saksnummer = "sak01",
        opprettetSke = Instant.parse("2024-06-16T13:33:05.672Z").toLocalDateTime(TimeZone.currentSystemDefault()),
        trekkpliktig = "987654321",
        skyldner = "12345678901",
        trekkstatus = "active",
        status = "MOTTATT",
        kid = "dette er kid",
        kontonummer = "12341212345",
        betalingsmottaker = "987654322",
        corrid = "dette_er_corrid",
        tidspunktSisteStatus = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
        tidspunktSendtOs = null,
        tidspunktOpprettet = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
    )

fun perioder():List<TrekkPeriodeTable>  = listOf(
        TrekkPeriodeTable(
            trekkPeriodeTableId = 1,
            sekvensnummer = 3,
            trekkidSke = "SKEID",
            trekkversjon = 2,
            datoStart = "2025-01-01",
            datoSlutt = "2025-02-28",
            sats = 2000.00,
            trekkAlternativ = "LOPM",
        ),
        TrekkPeriodeTable(
            trekkPeriodeTableId = 1,
            sekvensnummer = 3,
            trekkidSke = "SKEID",
            trekkversjon = 2,
            datoStart = "2025-03-01",
            datoSlutt = "2025-04-30",
            trekkAlternativ = "LOPP",
            sats = 15.0,
        ),
        TrekkPeriodeTable(
            trekkPeriodeTableId = 1,
            sekvensnummer = 3,
            trekkidSke = "SKEID",
            trekkversjon = 2,
            datoStart = "2025-05-01",
            datoSlutt = "2025-05-31",
            sats = 2000.00,
            trekkAlternativ = "LOPM",

        )
    )

val tilOs = """
{"dokument":
    {"transaksjonsId":"",
        "innrapporteringTrekk":
        {"aksjonskode":"NY",
            "kreditorIdTss":"80000345435",
            "kreditorTrekkId":"4",
            "debitorId":"01018941372",
            "kodeTrekkAlternativ":"LOPM",
            "kid":"17654202400",
            "kreditorsRef":"sak-2023-899",
            "kodeTrekktype":"KRED",
            "kilde":"?",
            "saldo":0.0,
            "prioritetFomDato":"2024-06-16",
            "perioder":
            {"periode": [
                {"periodeFomDato":"2023-06-13",
                    "periodeTomDato":"2024-11-30",
                    "sats":5000.0},
                "periode":
                {"periodeFomDato":"2024-12-01",
                    "periodeTomDato":"2024-12-31",
                    "sats":0.0},
                "periode":
                {"periodeFomDato":"2025-01-01",
                    "periodeTomDato":"",
                    "sats":5000.0}
                ]}
        }
    }
}
""".trimIndent()
val kvitteringMedFeil = """
{
    "mmel":
    {
        "systemId":"231-OPPD",
        "kodeMelding":"B199006F",
        "alvorlighetsgrad":"08",
        "beskrMelding":"Personen finnes ikke i PDL: 19628198007",
        "programId":"K231B199", "sectionNavn":"CA30-SJEKK-PDL"
    },
    "dokument":
    {
        "transaksjonsId": "transid1",
        "innrapporteringTrekk":
        {
            "aksjonskode":"NY",
            "kreditorIdTss":"971648198",
            "kreditorTrekkId":"1",
            "debitorId":"19628198007",
            "kodeTrekktype":"KRED",
            "kodeTrekkAlternativ":"LOPM",
            "kid":"17654202404",
            "kreditorsRef":"sak-2023-899",
            "kilde":"SOKO",
            "prioritetFomDato":"2024-06-16",
            "saldo": 0.0,
            "perioder":
            {
                "periode":
                [
                    {
                        "periodeFomDato":"2023-06-13",
                        "periodeTomDato":"2024-11-30",
                        "sats":5000.00
                    },
                    {
                        "periodeFomDato":"2024-12-01",
                        "periodeTomDato":"2024-12-31",
                        "sats":0.00
                    },
                    {
                        "periodeFomDato":"2025-01-01",
                        "sats":5000.00
                    }
                ]
            }
        }
    }
}
 """.trimIndent()