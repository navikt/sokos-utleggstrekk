package no.nav.sokos.utleggstrekk.service.gammel

import kotlinx.serialization.json.Json

import io.kotest.core.spec.style.BehaviorSpec

internal class LifecycleTest :
    BehaviorSpec({
        val json =
            Json {
                prettyPrint = true
                isLenient = true
                explicitNulls = false
            }

 /*       xcontext("disabled") {
            val testContainer = TestContainer()
            val dataSource = testContainer.dataSource
            val repository = Repository(dataSource)

            Given("Vi har mottatt utleggstrekk...  ") {
                val bodyFraSkatt = resourceToString("FraSkatt_Trekkversjon1_1Trekkalternativ-2trekk.json")
                val paleggstrekkFraSkatt = json.decodeFromString<List<Trekkpaalegg>>(bodyFraSkatt)

                then("lagres disse i database") {
                    dataSource.withTransaction { session ->
                        repository.saveAllNewUtleggstrekk(paleggstrekkFraSkatt, session)
                    }
                    dataSource.withTransaction { session ->
                        val rs =
                            session.createPreparedStatement(queryOf("""select count(*) from utleggstrekk""")).executeQuery()
                        rs.next() shouldBe true
                        rs.getInt(1) shouldBe 2

                        val rs2 =
                            session.createPreparedStatement(queryOf("""select count(*) from trekkperiode""")).executeQuery()
                        rs2.next() shouldBe true
                        rs2.getInt(1) shouldBe 4
                    }
                }
                then("sjekker at dataene er lagret riktig") {
                    dataSource.withTransaction { session ->
                        val dbdataTrekk = repository.fetchTrekkNotSendt(session)
                        val dbdataPerioder = repository.fetchPerioderForTrekkVersion(dbdataTrekk.first(), session)
                        dbdataTrekk.first().trekkidSke shouldBe paleggstrekkFraSkatt.first().trekkid
                        dbdataTrekk.first().trekkversjon shouldBe paleggstrekkFraSkatt.first().trekkversjon
                        dbdataTrekk.first().skyldner shouldBe paleggstrekkFraSkatt.first().skyldner
                        dbdataTrekk.first().sekvensnummer shouldBe paleggstrekkFraSkatt.first().sekvensnummer
                        dbdataPerioder.mapIndexed { i, periode ->
                            if (periode.trekkAlternativ == LOPP) {
                                periode.sats shouldBe
                                    paleggstrekkFraSkatt
                                        .first()
                                        .trekkstoerrelseForPeriode
                                        .get(i)
                                        .trekkprosent
                                        ?.trekkprosent
                            }
                            if (periode.trekkAlternativ == LOPM) {
                                periode.sats shouldBe
                                    paleggstrekkFraSkatt
                                        .first()
                                        .trekkstoerrelseForPeriode
                                        .get(i)
                                        .trekkbeloep
                                        ?.trekkbeloep
                            }
                            periode.datoStart shouldBe
                                paleggstrekkFraSkatt
                                    .first()
                                    .trekkstoerrelseForPeriode
                                    .get(i)
                                    .startdato
                            periode.datoSlutt shouldBeIn
                                arrayOf(
                                    "9999-12-31",
                                    paleggstrekkFraSkatt
                                        .first()
                                        .trekkstoerrelseForPeriode
                                        .get(i)
                                        .sluttdato,
                                )
                        }
                    }
                }
                then("Henter data fra database og sjekker perioder") {
                    val dbService = DatabaseService(testContainer.dataSource)
                    val behandleTrekkService = BehandleTrekkService(dbService)
                    val trekkSomSkalSendesMap = behandleTrekkService.lagTrekkSomSkalSendes()

                    trekkSomSkalSendesMap.size shouldBe 2
                    trekkSomSkalSendesMap.entries
                        .first()
                        .value.size shouldBe 2
                    trekkSomSkalSendesMap.entries
                        .last()
                        .value.size shouldBe 1
                    trekkSomSkalSendesMap.entries
                        .first()
                        .value
                        .first()
                        .dokument.innrapporteringTrekk.perioder.periode.size shouldBe
                        trekkSomSkalSendesMap.entries
                            .first()
                            .value
                            .last()
                            .dokument.innrapporteringTrekk.perioder.periode.size
                }
                then("hent fra databse og konverter til OS format") {
                    dataSource.withTransaction { session ->

                        val dbdataTrekk = repository.fetchTrekkNotSendt(session)
                        val dbperiode = repository.fetchPerioderForTrekkVersion(dbdataTrekk[0], session)

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
                            dbperiode.groupBy { it.trekkAlternativ }.get(LOPM)?.size shouldBe 3
                        }
                        withClue("Det skal være 3 periode med LOPP, 2 med 0.0") {
                            dbperiode.groupBy { it.trekkAlternativ }.get(LOPP)?.size shouldBe 3
                        }
                        val osdok = dbdataTrekk[0].toTrekkDokument(dbperiode)
                        withClue("osDokumentet skal ha samme info som i trekk og perioder") {
                            with(osdok.dokument.innrapporteringTrekk) {
                                kreditorTrekkId shouldBe dbdataTrekk[0].trekkIdWithSuffix(dbperiode[0].trekkAlternativ)
                                kid shouldBe dbdataTrekk[0].kid
                                kodeTrekkAlternativ shouldBe dbperiode[0].trekkAlternativ
                            }
                        }
                    }
                }
            }
        }*/
    })