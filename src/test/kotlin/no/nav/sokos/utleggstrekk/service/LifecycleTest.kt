package no.nav.sokos.utleggstrekk.service

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import no.nav.sokos.utleggstrekk.TestContainer
import no.nav.sokos.utleggstrekk.database.Repository.fetchPerioderForTrekkVersion
import no.nav.sokos.utleggstrekk.database.Repository.fetchTrekkNotSendt
import no.nav.sokos.utleggstrekk.database.Repository.saveAllNewUtleggstrekk
import no.nav.sokos.utleggstrekk.domene.ske.Trekkpaalegg
import no.nav.sokos.utleggstrekk.util.Responses
import no.nav.sokos.utleggstrekk.utils.JavaLocaldateTimeSerializer
import no.nav.sokos.utleggstrekk.utils.LocalDateSerializer
import no.nav.sokos.utleggstrekk.utils.LocalDateTimeSerializer
import no.nav.sokos.utleggstrekk.utils.ZonedDateTimeSerializer
import no.nav.sokos.utleggstrekk.utils.toTrekkDokument

internal class LifecycleTest :
    BehaviorSpec({
        val json = Json {
            prettyPrint = true
            isLenient = true
            explicitNulls = false
            serializersModule = SerializersModule {
                contextual(JavaLocaldateTimeSerializer)
                contextual(LocalDateTimeSerializer)
                contextual(LocalDateSerializer)
                contextual(ZonedDateTimeSerializer)
            }
        }

        val testContainer = TestContainer()
        testContainer.migrate()

        Given("Vi har mottatt utleggstrekk...  ") {
            val bodyFraSkatt = Responses.utleggsTrekkListeFraSkatt
            val paleggstrekkFraSkatt = json.decodeFromString<List<Trekkpaalegg>>(bodyFraSkatt)

            then("lagres disse i database") {
                testContainer.dataSource.connection.use {
                    it.saveAllNewUtleggstrekk(paleggstrekkFraSkatt)

                    val rs = it.prepareStatement("""select count(*) from utleggstrekk""").executeQuery()
                    rs.next() shouldBe true
                    rs.getInt(1) shouldBe 2

                    val rs2 = it.prepareStatement("""select count(*) from trekkperiode""").executeQuery()
                    rs2.next() shouldBe true
                    rs2.getInt(1) shouldBe 4
                }
            }
            then("sjekker at dataene er lagret riktig") {
                val dbdataTrekk = testContainer.dataSource.connection.fetchTrekkNotSendt()
                val dbdataPerioder = testContainer.dataSource.connection.fetchPerioderForTrekkVersion(dbdataTrekk.first())
                dbdataTrekk.first().trekkidSke shouldBe paleggstrekkFraSkatt.first().trekkid
                dbdataTrekk.first().trekkversjon shouldBe paleggstrekkFraSkatt.first().trekkversjon
                dbdataTrekk.first().skyldner shouldBe paleggstrekkFraSkatt.first().skyldner
                dbdataTrekk.first().sekvensnummer shouldBe paleggstrekkFraSkatt.first().sekvensnummer
                dbdataPerioder.mapIndexed { i, periode ->
                    if (periode.trekkAlternativ == "LOPP") {
                        periode.sats shouldBe paleggstrekkFraSkatt.first().trekkstoerrelseForPeriode.get(i).trekkprosent?.trekkprosent
                    }
                    if (periode.trekkAlternativ == "LOPM") {
                        periode.sats shouldBe paleggstrekkFraSkatt.first().trekkstoerrelseForPeriode.get(i).trekkbeloep?.trekkbeloep
                    }
                    periode.datoStart shouldBe paleggstrekkFraSkatt.first().trekkstoerrelseForPeriode.get(i).startdato
                    periode.datoSlutt shouldBeIn arrayOf("9999-12-31", paleggstrekkFraSkatt.first().trekkstoerrelseForPeriode.get(i).sluttdato)
                }
            }
            then("Henter data fra database og sjekker perioder"){
                val dbService =  DatabaseService(testContainer.dataSource)
                val behandleTrekkService = BehandleTrekkService(dbService)
                val trekkSomSkalSendes = behandleTrekkService.lagTrekkSomSkalSendes()
                trekkSomSkalSendes.size shouldBe 2
                trekkSomSkalSendes[0].second.size shouldBe 2
                trekkSomSkalSendes[1].second.size shouldBe 1
                trekkSomSkalSendes[0].second[0].dokument.innrapporteringTrekk.perioder.periode.size shouldBe
                        trekkSomSkalSendes[0].second[1].dokument.innrapporteringTrekk.perioder.periode.size
            }
            then("hent fra databse og konverter til OS format") {
                val dbdataTrekk = testContainer.dataSource.connection.fetchTrekkNotSendt()
                val dbperiode = testContainer.dataSource.connection.fetchPerioderForTrekkVersion(dbdataTrekk[0])

                withClue("Det skal være 2 trekk i db") {
                    dbdataTrekk.size shouldBe 2
                }
                withClue("Det skal være 6 perioder for trekk 1") {
                    dbperiode.size shouldBe 6
                }
                withClue("Det skal være 2 trekkalternativer for trekk 1") {
                    dbperiode.groupBy { it.trekkAlternativ }.size shouldBe 2
                }
                withClue("Det skal være 3 perioder med LOPM, 1 med 0.0") {
                    dbperiode.groupBy { it.trekkAlternativ }.get("LOPM")?.size shouldBe 3
                }
                withClue("Det skal være 3 periode med LOPP, 2 med 0.0") {
                    dbperiode.groupBy { it.trekkAlternativ }.get("LOPP")?.size shouldBe 3
                }
                val osdok = dbdataTrekk[0].toTrekkDokument(dbperiode)
                withClue("osDokumentet skal ha samme info som i trekk og perioder") {
                    with(osdok.dokument.innrapporteringTrekk) {
                        kreditorTrekkId shouldBe dbdataTrekk[0].trekkidSke + dbperiode[0].trekkAlternativ.get(3)
                        kid shouldBe dbdataTrekk[0].kid
                        kodeTrekkAlternativ shouldBe dbperiode[0].trekkAlternativ
                    }
                }

            }
        }
    })