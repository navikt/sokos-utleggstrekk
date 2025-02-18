package no.nav.sokos.utleggstrekk.database

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import no.nav.sokos.utleggstrekk.TestContainer
import no.nav.sokos.utleggstrekk.database.Repository.fetchAllTrekkNotSent
import no.nav.sokos.utleggstrekk.database.Repository.fetchPerioderForTrekk
import no.nav.sokos.utleggstrekk.database.Repository.saveAllNewUtleggstrekk
import no.nav.sokos.utleggstrekk.domene.LocalDateSerializer
import no.nav.sokos.utleggstrekk.domene.LocalDateTimeSerializer
import no.nav.sokos.utleggstrekk.domene.ZonedDateTimeSerializer
import no.nav.sokos.utleggstrekk.domene.ske.Trekkpaalegg
import no.nav.sokos.utleggstrekk.util.Responses

internal class RepositoryTest :
    BehaviorSpec({
        //   extensions(TestContainer)
        val json = Json {
            prettyPrint = true
            isLenient = true
            decodeEnumsCaseInsensitive = true
            explicitNulls = false
            serializersModule = SerializersModule {
                contextual(ZonedDateTimeSerializer)
                contextual(LocalDateTimeSerializer)
                contextual(LocalDateSerializer)
            }
        }

            val testContainer = TestContainer()
            testContainer.migrate()

        Given("Vi har mottatt utleggstrekk...  ") {
            val bodyFraSkatt = Responses.utleggsTrekkListeFraSkatt
            val paleggstrekkFraSkatt = json.decodeFromString<List<Trekkpaalegg>>(bodyFraSkatt)

            then("insert i database") {
                testContainer.dataSource.connection.use {
                    it.saveAllNewUtleggstrekk(paleggstrekkFraSkatt)

                    val rs = it.prepareStatement("""select count(*) from trekkpalegg""").executeQuery()
                    rs.next() shouldBe true
                    rs.getInt(1) shouldBe 2

                    val rs2 = it.prepareStatement("""select count(*) from trekkpaleggperiode""").executeQuery()
                    rs2.next() shouldBe true
                    rs2.getInt(1) shouldBe 4
                }
            }
            then("sjekk at dataene er riktige"){
                val dbdataTrekk = testContainer.dataSource.connection.fetchAllTrekkNotSent()
                val dbdataPerioder = testContainer.dataSource.connection.fetchPerioderForTrekk(dbdataTrekk.first())
                dbdataTrekk.first().trekkidSke shouldBe paleggstrekkFraSkatt.first().trekkid
                dbdataTrekk.first().trekkversjon shouldBe  paleggstrekkFraSkatt.first().trekkversjon
                dbdataTrekk.first().skyldner shouldBe  paleggstrekkFraSkatt.first().skyldner
                dbdataTrekk.first().sekvensnummer shouldBe  paleggstrekkFraSkatt.first().sekvensnummer
                dbdataPerioder.mapIndexed { i, periode ->
                    periode.trekkprosent shouldBeIn  arrayOf( 0.0, paleggstrekkFraSkatt.first().trekkstoerrelseForPeriode.get(i).trekkprosent?.trekkprosent)
                    periode.trekkbelop shouldBeIn arrayOf( 0.0, paleggstrekkFraSkatt.first().trekkstoerrelseForPeriode.get(i).trekkbeloep?.trekkbeloep)
                    periode.datoStart shouldBe paleggstrekkFraSkatt.first().trekkstoerrelseForPeriode.get(i).startdato
                    periode.datoSlutt shouldBeIn arrayOf( "", paleggstrekkFraSkatt.first().trekkstoerrelseForPeriode.get(i).sluttdato)
                }
            }
        }
    })