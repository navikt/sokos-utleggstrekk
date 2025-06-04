package no.nav.sokos.utleggstrekk

import com.google.gson.Gson
import io.kotest.core.spec.style.FunSpec
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import no.nav.sokos.utleggstrekk.database.model.TrekkPeriodeTable
import no.nav.sokos.utleggstrekk.database.model.UtleggstrekkTable
import no.nav.sokos.utleggstrekk.domene.nav.TrekkTilOppdrag
import no.nav.sokos.utleggstrekk.domene.ske.Trekkpaalegg
import no.nav.sokos.utleggstrekk.utils.LocalDateSerializer
import no.nav.sokos.utleggstrekk.utils.LocalDateTimeSerializer
import no.nav.sokos.utleggstrekk.utils.ZonedDateTimeSerializer
import org.testcontainers.shaded.com.google.common.reflect.TypeToken
import java.sql.Timestamp
import java.time.LocalDateTime

internal class KenTesterJson : FunSpec({
    val json = Json {
        prettyPrint = true
        isLenient = true
        explicitNulls = false
        serializersModule = SerializersModule {
            contextual(ZonedDateTimeSerializer)
            contextual(LocalDateTimeSerializer)
            contextual(LocalDateSerializer)
        }}


    test("db timeformats") {
        val strDato = "2024-06-17T13:33:05.672Z"
        val instant:Instant = strDato.toInstant()
        val sql: java.sql.Timestamp = Timestamp(instant.toEpochMilliseconds())
        val l: LocalDateTime = sql.toLocalDateTime()
        println("sd: $strDato, i: $instant t: $sql l: $l")
    }

    test("parsing av reponse fra skatt") {
        println(bodyFraSkatt)
        val typeToken = object : TypeToken<List<Trekkpaalegg>>() {}.type

            val trekkpaalegg: List<Trekkpaalegg> = json.decodeFromString<List<Trekkpaalegg>>(bodyFraSkatt)
        println("antall: ${trekkpaalegg.size}")
        trekkpaalegg.forEach {
            println("trekkbeløp: ${it.trekkstoerrelseForPeriode[0].trekkbeloep.let { 
                if (it == null || it.trekkbeloep == null) java.sql.Types.NULL.toDouble()
                else it.trekkbeloep
            }}")
            println("trekkprosent: ${it.trekkstoerrelseForPeriode[0].trekkprosent.let { 
                if (it == null || it.trekkprosent == null) java.sql.Types.NULL.toDouble()
                else it.trekkprosent
            }}")
            println("OBJECT*************")
            println(it)
            println("JSON***************")
            println(Json.encodeToString(it))
        }
    }


    test("Sjekk reponse body"){
        val trekkpaalegg: List<Trekkpaalegg> = json.decodeFromString<List<Trekkpaalegg>>(bodyFraSkatt)
        //println(Gson().fromJson(trekkJson, TrekkTilOppdrag.javaClass))

    }

    test("sjekke Kvitteringsmelding med feil"){
        val fraOs = json.decodeFromString<TrekkTilOppdrag>(kvitteringMedFeil)
        println(fraOs)
        println(Gson().toJson(fraOs))
    }

})

val bodyFraSkatt = """
        [{"trekkid":"1","trekkversjon":1,"sekvensnummer":1,"opprettet":"2024-06-16T13:33:05.672Z","saksnummer":"sak-2023-899","trekkpliktig":"889640782","skyldner":"19628198007","trekkstatus":"aktiv","trekkstoerrelseForPeriode":[{"startdato":"2023-06-13","sluttdato":"2024-11-30","trekkbeloep":{"trekkbeloep":5000.0}},{"startdato":"2024-12-01","sluttdato":"2024-12-31","trekkbeloep":{"trekkbeloep":0.0}},{"startdato":"2025-01-01","trekkbeloep":{"trekkbeloep":5000.0}}],"betalingsinformasjon":{"betalingsmottaker":"971648198","kidnummer":"17654202404","kontonummer":"76940512057"}},{"trekkid":"2_xx","trekkversjon":1,"sekvensnummer":2,"opprettet":"2024-06-16T14:33:05.672Z","saksnummer":"sak-2023-900","trekkpliktig":"889640782","skyldner":"11656296129","trekkstatus":"aktiv","trekkstoerrelseForPeriode":[{"startdato":"2023-06-13","sluttdato":"2024-11-30","trekkbeloep":{"trekkbeloep":800.5}}],"betalingsinformasjon":{"betalingsmottaker":"971648198","kidnummer":"45645202404","kontonummer":"76940512057"}}]
    """.trimIndent()

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