package no.nav.sokos.utleggstrekk.service

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import no.nav.sokos.utleggstrekk.TestContainer
import no.nav.sokos.utleggstrekk.database.Repository.fetchAllPerioderForTrekkVersion
import no.nav.sokos.utleggstrekk.database.Repository.fetchAllTrekkNotSent
import no.nav.sokos.utleggstrekk.database.Repository.fetchAllTrekkWithoutTrekkAlternativ
import no.nav.sokos.utleggstrekk.database.Repository.fetchPerioderForTrekkWithTrekkAlternativ
import no.nav.sokos.utleggstrekk.database.Repository.insertGeneratedTrekkpalegg
import no.nav.sokos.utleggstrekk.database.Repository.saveAllNewUtleggstrekk
import no.nav.sokos.utleggstrekk.database.Repository.updateWithTrekkAlternativ
import no.nav.sokos.utleggstrekk.domene.ske.Trekkpaalegg
import no.nav.sokos.utleggstrekk.util.Responses
import no.nav.sokos.utleggstrekk.utils.JavaLocaldateTimeSerializer
import no.nav.sokos.utleggstrekk.utils.LocalDateSerializer
import no.nav.sokos.utleggstrekk.utils.LocalDateTimeSerializer
import no.nav.sokos.utleggstrekk.utils.ZonedDateTimeSerializer
import no.nav.sokos.utleggstrekk.utils.oppdaterTrekkMedForskjelligSatstype
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
                val dbdataTrekk = testContainer.dataSource.connection.fetchAllTrekkNotSent()
                val dbdataPerioder = testContainer.dataSource.connection.fetchAllPerioderForTrekkVersion(dbdataTrekk.first())
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
                    periode.datoSlutt shouldBeIn arrayOf("", paleggstrekkFraSkatt.first().trekkstoerrelseForPeriode.get(i).sluttdato)
                }
            }
            then("sett riktig Trekkalternativ og kreditors trekkId (til os) med riktig suffix") {
                val dbDataFor = testContainer.dataSource.connection.fetchAllTrekkWithoutTrekkAlternativ()
                val perioder1 = testContainer.dataSource.connection.fetchAllPerioderForTrekkVersion(dbDataFor[0])
                val perioder2 = testContainer.dataSource.connection.fetchAllPerioderForTrekkVersion(dbDataFor[1])
                dbDataFor.size shouldBe 2

                val oppdatert1 = oppdaterTrekkMedForskjelligSatstype(dbDataFor[0], perioder1)
                val oppdatert2 = oppdaterTrekkMedForskjelligSatstype(dbDataFor[1], perioder2)
                oppdatert1.size shouldBe 2
                oppdatert2.size shouldBe 1

                testContainer.dataSource.connection.updateWithTrekkAlternativ(oppdatert1[0])
                testContainer.dataSource.connection.updateWithTrekkAlternativ(oppdatert2[0])
                testContainer.dataSource.connection.insertGeneratedTrekkpalegg(oppdatert1[1])
                val dbDataEtter = testContainer.dataSource.connection.fetchAllTrekkNotSent()
                dbDataEtter.size shouldBe 3
            }

            then("hent fra databse og konverter til OS format") {
                val dbdataTrekk = testContainer.dataSource.connection.fetchAllTrekkNotSent()
                dbdataTrekk.size shouldBe 3
                dbdataTrekk.forEach { trekk ->
                    val dbperiode = testContainer.dataSource.connection.fetchPerioderForTrekkWithTrekkAlternativ(trekk)
                    val osdok = trekk.toTrekkDokument(dbperiode)
                    with(osdok.dokument.innrapporteringTrekk) {
                        kreditorTrekkId shouldBe trekk.trekkidSkeOS
                        kid shouldBe  trekk.kid
                        kodeTrekkAlternativ shouldBe trekk.trekkAlternativ
                    }
                }
            }
        }
    })