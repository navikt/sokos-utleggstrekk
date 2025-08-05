package no.nav.sokos.utleggstrekk.service

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.shouldBe

import no.nav.sokos.utleggstrekk.config.http.jsonConfig
import no.nav.sokos.utleggstrekk.database.Repository.fetchPerioderForTrekkVersion
import no.nav.sokos.utleggstrekk.database.Repository.fetchTrekkNotSendt
import no.nav.sokos.utleggstrekk.database.Repository.saveAllNewUtleggstrekk
import no.nav.sokos.utleggstrekk.database.model.TrekkPeriodeTable
import no.nav.sokos.utleggstrekk.domene.nav.TrekkAlternativ
import no.nav.sokos.utleggstrekk.domene.ske.Trekkpaalegg
import no.nav.sokos.utleggstrekk.util.TestContainer
import no.nav.sokos.utleggstrekk.util.TestUtils.fileAsString
import no.nav.sokos.utleggstrekk.utils.toTrekkDokument

internal class LifecycleTest :
    BehaviorSpec({
        val testContainer = TestContainer()
        Given("Vi har mottatt utleggstrekk...  ") {

            val bodyFraSkatt = fileAsString("FraSkatt_Trekkversjon1_1Trekkalternativ-2trekk.json")
            val paleggstrekkFraSkatt = jsonConfig.decodeFromString<List<Trekkpaalegg>>(bodyFraSkatt)

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
                val firstTrekk = paleggstrekkFraSkatt.first()

                val firstTrekkNotSendt =
                    testContainer.dataSource.connection
                        .fetchTrekkNotSendt()
                        .first()
                with(firstTrekkNotSendt) {
                    trekkidSke shouldBe firstTrekk.trekkid
                    trekkversjon shouldBe firstTrekk.trekkversjon
                    skyldner shouldBe firstTrekk.skyldner
                    sekvensnummer shouldBe firstTrekk.sekvensnummer
                }

                testContainer.dataSource.connection.fetchPerioderForTrekkVersion(firstTrekkNotSendt).forEachIndexed { i, periode ->
                    val firstTrekkStoerrelse = firstTrekk.trekkstoerrelseForPeriode[i]

                    when (periode.trekkAlternativ) {
                        TrekkAlternativ.LOPM.value -> {
                            // TODO: Kan trekkbeloep og trekkprosent hete "sats"?
                            periode.sats shouldBe firstTrekkStoerrelse.trekkbeloep?.trekkbeloep
                        }

                        TrekkAlternativ.LOPP.value -> {
                            periode.sats shouldBe firstTrekkStoerrelse.trekkprosent?.trekkprosent
                        }
                    }

                    // TODO: Disse burde hete det samme
                    periode.datoStart shouldBe firstTrekkStoerrelse.startdato
                    periode.datoSlutt.shouldBeIn(firstTrekkStoerrelse.sluttdato, "9999-12-31")
                }
            }

            then("Henter data fra database og sjekker perioder") {
                val trekkSomSkalSendesMap = BehandleTrekkService(DatabaseService(testContainer.dataSource)).lagTrekkSomSkalSendes()

                with(trekkSomSkalSendesMap.entries) {
                    val firstTrekkSomSkalSendes = first().value
                    val lastTrekkSomSkalSendes = last().value

                    lastTrekkSomSkalSendes.size shouldBe 1

                    firstTrekkSomSkalSendes.size shouldBe 2
                    firstTrekkSomSkalSendes
                        .first()
                        .dokument.innrapporteringTrekk.perioder.periode.size shouldBe
                        firstTrekkSomSkalSendes
                            .last()
                            .dokument.innrapporteringTrekk.perioder.periode.size
                }
            }
            then("hent fra databse og konverter til OS format") {
                val trekkNotSendt = testContainer.dataSource.connection.fetchTrekkNotSendt()
                val firstTrekkNotSendt = trekkNotSendt.first()
                val dbperiode = testContainer.dataSource.connection.fetchPerioderForTrekkVersion(firstTrekkNotSendt)
                val trekkAlternativMap = dbperiode.groupBy { it.trekkAlternativ }

                withClue("Det skal være 2 trekk i db") {
                    trekkNotSendt.size shouldBe 2
                }
                withClue("Det skal være 6 perioder for trekk 1") {
                    dbperiode.size shouldBe 6
                }

                withClue("Det skal være 3 perioder med LOPM, 1 med 0.0") {
                    val lopmPerioder: List<TrekkPeriodeTable> = trekkAlternativMap[TrekkAlternativ.LOPM.value]!!

                    lopmPerioder.size shouldBe 3
                    lopmPerioder.count { it.sats == 0.0 } shouldBe 1
                }
                withClue("Det skal være 3 periode med LOPP, 2 med 0.0") {
                    val loppPerioder: List<TrekkPeriodeTable> = trekkAlternativMap[TrekkAlternativ.LOPP.value]!!

                    loppPerioder.size shouldBe 3
                    loppPerioder.count { it.sats == 0.0 } shouldBe 2
                }

                val osdok = firstTrekkNotSendt.toTrekkDokument(dbperiode)
                withClue("osDokumentet skal ha samme info som i trekk og perioder") {
                    with(osdok.dokument.innrapporteringTrekk) {
                        kreditorTrekkId shouldBe firstTrekkNotSendt.trekkidSke + dbperiode[0].trekkAlternativ[3]
                        kid shouldBe firstTrekkNotSendt.kid
                        kodeTrekkAlternativ shouldBe dbperiode[0].trekkAlternativ
                    }
                }
            }
        }
    })
