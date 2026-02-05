package no.nav.sokos.utleggstrekk.database

import java.time.LocalDate
import java.time.LocalDateTime

import kotlin.time.Duration.Companion.seconds

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.ranges.shouldBeIn
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotliquery.queryOf

import no.nav.sokos.utleggstrekk.config.jsonConfig
import no.nav.sokos.utleggstrekk.database.Repository.TransaksjonOsTable.TIDSPUNKT_SENDT_COLUMN
import no.nav.sokos.utleggstrekk.database.Repository.TransaksjonOsTable.TRANSAKSJONS_ID_COLUMN
import no.nav.sokos.utleggstrekk.database.Repository.TransaksjonOsTable.TRANSAKSJONS_ID_PARAM
import no.nav.sokos.utleggstrekk.database.model.BetalingsinformasjonFraSkatt
import no.nav.sokos.utleggstrekk.database.model.Feilmelding
import no.nav.sokos.utleggstrekk.database.model.KvitteringStatus
import no.nav.sokos.utleggstrekk.database.model.PeriodeFraSkatt
import no.nav.sokos.utleggstrekk.database.model.PeriodeTilOS
import no.nav.sokos.utleggstrekk.database.model.SkattTrekkStatus
import no.nav.sokos.utleggstrekk.database.model.SkattTrekkStatus.BEHANDLET
import no.nav.sokos.utleggstrekk.database.model.SkattTrekkStatus.MOTTATT
import no.nav.sokos.utleggstrekk.database.model.TransaksjonsStatus
import no.nav.sokos.utleggstrekk.database.model.TrekkFraSkatt
import no.nav.sokos.utleggstrekk.domene.nav.Aksjonskode
import no.nav.sokos.utleggstrekk.domene.nav.DokumentTilOppdrag
import no.nav.sokos.utleggstrekk.domene.nav.InnrapporteringTrekk
import no.nav.sokos.utleggstrekk.domene.nav.KvitteringFraOppdrag
import no.nav.sokos.utleggstrekk.domene.nav.OSDto
import no.nav.sokos.utleggstrekk.domene.nav.Periode
import no.nav.sokos.utleggstrekk.domene.nav.Perioder
import no.nav.sokos.utleggstrekk.domene.nav.TrekkAlternativ
import no.nav.sokos.utleggstrekk.domene.ske.Betalingsinformasjon
import no.nav.sokos.utleggstrekk.domene.ske.Trekkpaalegg
import no.nav.sokos.utleggstrekk.domene.ske.TrekkstorrelseForPeriode
import no.nav.sokos.utleggstrekk.listener.DBListener
import no.nav.sokos.utleggstrekk.listener.DBListener.repository
import no.nav.sokos.utleggstrekk.util.insertRawSQL
import no.nav.sokos.utleggstrekk.util.resourceToString

class RepositoryTest :
    BehaviorSpec({
        extensions(DBListener)

        val dummyInnrapporteringTrekk =
            InnrapporteringTrekk(
                aksjonskode = Aksjonskode.NY,
                kreditorIdTss = "KreditorIdTSS",
                kreditorTrekkId = "KreditorTrekkId",
                kreditorsRef = "KreditorsRef",
                debitorId = "debitorId",
                kodeTrekkAlternativ = TrekkAlternativ.LOPM,
                kid = "Kidnummer",
                prioritetFomDato = LocalDate.now().toString(),
                perioder =
                    Perioder(
                        listOf(
                            Periode(
                                periodeFomDato = "2024-01-01",
                                periodeTomDato = "2024-01-31",
                                sats = 5000.0,
                            ),
                        ),
                    ),
            )

        fun lagDokumentTilOppdrag(transaksjonsId: String): DokumentTilOppdrag =
            DokumentTilOppdrag(
                transaksjonsId,
                innrapporteringTrekk = dummyInnrapporteringTrekk,
            )

        Given("Det finnes to trekk som er behandlet hvor ett er sendt.") {
            DBListener.clearDB()
            val fraskatt = resourceToString("InitTrekk/Fra_Skatt_Trekk1_versjon1_en_periode_belop.json")
            val skalSendes = jsonConfig.decodeFromString<List<Trekkpaalegg>>(fraskatt).first()
            repository.insertTrekkFraSkatt(skalSendes)
            val dto =
                OSDto(
                    transaksjonID = "SkalSendes",
                    skalSendes.trekkid,
                    trekkversjon = 1,
                    lagDokumentTilOppdrag("SkalSendes").innrapporteringTrekk,
                    "dummydokument",
                )
            repository.insertTransaksjonTilOs(dto)
            repository.insertTrekkFraSkatt(skalSendes.copy(trekkid = "SkalOgsåSendes"))

            val trekkSomErSendt = skalSendes.copy(trekkid = "SkalIkkeSendes")
            repository.insertTrekkFraSkatt(skalSendes.copy(trekkid = "SkalIkkeSendes"))
            val dtoSomErSendt =
                OSDto(
                    transaksjonID = "SkalIkkeSendes",
                    trekkSomErSendt.trekkid,
                    trekkversjon = 1,
                    lagDokumentTilOppdrag("SkalIkkeSendes").innrapporteringTrekk,
                    "dummydokument",
                )
            repository.insertTransaksjonTilOs(dtoSomErSendt)
            repository.updateTransaksjonSendt(dtoSomErSendt.transaksjonID)

            Then("Finnes det en transaksjon som ikke er sendt") {
                val ikkeSendt = repository.getTransaksjonerTilOsSomIkkeErSendt()
                ikkeSendt.shouldHaveSize(1)
            }
            Then("Den sendte transaksjonen har fått oppdatert tidspunkt") {
                repository.withTransaction { session ->
                    session.list(
                        queryOf(
                            "SELECT $TIDSPUNKT_SENDT_COLUMN FROM transaksjon_os WHERE $TRANSAKSJONS_ID_COLUMN=:transaksjonId",
                            mapOf(TRANSAKSJONS_ID_PARAM to dtoSomErSendt.transaksjonID),
                        ),
                    ) { row -> row.localDateTime(TIDSPUNKT_SENDT_COLUMN) } shouldNotBe null
                }
            }
        }

        Given("Vi henter perioder for trekk med én versjon") {
            DBListener.clearDB()
            val trekkpaalegg1 = jsonConfig.decodeFromString<List<Trekkpaalegg>>(resourceToString("InitTrekk/Fra_Skatt_Trekk1_versjon1_en_periode_belop.json")).first()
            val idtrekkpaalegg1 = repository.insertTrekkFraSkatt(trekkpaalegg1)
            idtrekkpaalegg1.shouldNotBeNull()

            val perioder: List<PeriodeFraSkatt> = repository.getPerioderForTrekkVersjon(idtrekkpaalegg1)

            perioder.shouldHaveSize(1)

            val trekkpaalegg2 = jsonConfig.decodeFromString<List<Trekkpaalegg>>(resourceToString("InitTrekk/Fra_Skatt_Trekk2_versjon1_to_perioder_belop.json")).first()
            val idtrekkpaalegg2 = repository.insertTrekkFraSkatt(trekkpaalegg2)
            idtrekkpaalegg2.shouldNotBeNull()

            val perioder2: List<PeriodeFraSkatt> = repository.getPerioderForTrekkVersjon(idtrekkpaalegg2)
            perioder2.shouldHaveSize(2)
        }

        Given("Vi henter perioder for trekk med flere versjoner") {
            DBListener.clearDB()
            val trekkpaalegg1 = jsonConfig.decodeFromString<List<Trekkpaalegg>>(resourceToString("InitTrekk/Fra_Skatt_Trekk1_versjon1_en_periode_belop.json")).first()

            val idtrekkpaalegg1: Long? = repository.insertTrekkFraSkatt(trekkpaalegg1)
            idtrekkpaalegg1.shouldNotBeNull()

            val trekkpaalegg2 = jsonConfig.decodeFromString<List<Trekkpaalegg>>(resourceToString("InitTrekk/Fra_skatt_Trekk1_versjon2_endret_belop.json")).first()
            val idtrekkpaalegg2 = repository.insertTrekkFraSkatt(trekkpaalegg2)
            idtrekkpaalegg2.shouldNotBeNull()

            When("getPerioderForTrekkVersjon") {

                Then("Skal perioder hentes kun for trekk med gitt versjon, sekvensnummer og trekkid") {
                    val perioderVersjon1 = repository.getPerioderForTrekkVersjon(idtrekkpaalegg1)

                    perioderVersjon1.shouldHaveSize(1)
                    val perioderVersjon2 = repository.getPerioderForTrekkVersjon(idtrekkpaalegg2)

                    perioderVersjon2.shouldHaveSize(2)
                }
            }
        }

        Given("Data fra skatt skal lagres") {
            DBListener.clearDB()
            val trekkpaalegg = jsonConfig.decodeFromString<List<Trekkpaalegg>>(resourceToString("InitTrekk/Fra_Skatt_Trekk1_versjon1_en_periode_belop.json")).first()

            When("Trekkpålegg lagres") {
                val id = repository.insertTrekkFraSkatt(trekkpaalegg)
                id.shouldNotBeNull()

                eventually(duration = 1.seconds) {
                    val lagretTrekk = getTrekkFraSkatt(id)
                    lagretTrekk.shouldNotBeNull()
                    Then("Skal Trekkpålegg lagres i tabellen 'fraskatt'") {

                        compareTrekk(trekkpaalegg, lagretTrekk)
                    }
                    And("TrekkstørrelseForPeriode skal lagres i tabellen 'periOde'") {
                        val lagretPeriode = repository.getPerioderForTrekk(lagretTrekk)
                        comparePerioder(trekkpaalegg.trekkstoerrelseForPeriode, lagretPeriode)
                    }
                    And("Betalingsinformasjon skal lagres i tabellen 'betalingsinformasjonfraskatt'") {
                        val lagretBetalingsinformasjon = repository.getBetalingsinformasjonForTrekk(id)
                        lagretBetalingsinformasjon.shouldNotBeNull()
                        compareBetalingsinformasjon(trekkpaalegg.betalingsinformasjon, lagretBetalingsinformasjon)
                    }
                }
            }
        }

        Given("Feilmelding skal lagres") {
            val kvittering = jsonConfig.decodeFromString<KvitteringFraOppdrag>(resourceToString("kvittering-feil.json"))
            // Dummy transaksjon for å tilfredstille constraint.
            repository.insertTransaksjonTilOs(
                OSDto(
                    "0cf39d33-8b50-4694-8d70-7ff06c35e42a",
                    trekkIDSke = "10013",
                    trekkversjon = 1,
                    InnrapporteringTrekk(
                        Aksjonskode.NY,
                        "navtrekkid",
                        "tss",
                        "1",
                        "1",
                        "1",
                        "TRK1",
                        TrekkAlternativ.LOPM,
                        "kid",
                        "SOKOSUTLEGG",
                        1.0,
                        null,
                        null,
                        null,
                    ),
                    "fakedokument",
                ),
            )

            repository.insertFeilmeldingFraOS(kvittering)

            Then("Skal trekkid, trekkalternativ, corrid, feilkode og beskrivelse lagres") {
                val feilmelding = repository.getFeilmeldingerFraOS(kvittering.dokument.transaksjonsId)
                feilmelding.shouldNotBeNull()
                compareFeilmelding(kvittering, feilmelding)
            }
        }
        Given("Transaksjon til OS skal lagres") {
            val dto =
                OSDto(
                    transaksjonID = "123id",
                    "Et trekk",
                    trekkversjon = 1,
                    lagDokumentTilOppdrag("TransaksjonIDa").innrapporteringTrekk,
                    "",
                )
            repository.insertTransaksjonTilOs(dto)

            When("Transaksjon er lagret i tabellen 'transaksjon_os'") {
                val transaksjonTilOs = repository.getTransaksjonTilOs(dto.transaksjonID)
                transaksjonTilOs.shouldNotBeNull()

                Then("Skal transaksjonstatus skal være TransaksjonsStatus.IKKE_SENDT") {
                    transaksjonTilOs.transaksjonStatus shouldBe TransaksjonsStatus.IKKE_SENDT
                }
                And("KvitteringStatus skal være KvitteringStatus.IKKE_MOTTATT") {
                    transaksjonTilOs.kvitteringStatus shouldBe KvitteringStatus.IKKE_MOTTATT
                }
                And("Tidspunktsendt skal være nå") {
                    val now = dbNow()
                    transaksjonTilOs.tidspunktSendt?.shouldBeIn(now.minusSeconds(10)..now)
                }
                And("Tidspunktsistestatus skal være nå") {
                    val now = dbNow()
                    transaksjonTilOs.tidspunktSisteStatus?.shouldBeIn(now.minusSeconds(10)..now)
                }
                And("Enumverdier skal lagres korrekt") {
                    transaksjonTilOs.aksjonskode shouldBe Aksjonskode.NY
                    transaksjonTilOs.trekkAlternativ shouldBe TrekkAlternativ.LOPM
                }
            }

            When("Transaksjonstatus oppdateres") {
                repository.updateTransaksjonSendt(dto.transaksjonID)
                val transaksjonTilOs = repository.getTransaksjonTilOs(dto.transaksjonID)
                transaksjonTilOs.shouldNotBeNull()
                Then("Skal transaksjonen oppdateres med ny transaksjonstatus") {

                    transaksjonTilOs.transaksjonStatus shouldBe TransaksjonsStatus.SENDT
                }
                And("Tidspunktsistestatus skal være nå") {
                    val now = dbNow()
                    transaksjonTilOs.tidspunktSisteStatus?.shouldBeIn(now.minusSeconds(10)..now)
                }
            }
            When("Transaksjon oppdateres med kvitteringsstatus og navtrekkid") {
                val nyKvitteringStatus = KvitteringStatus.OK
                val nyNavTrekkId = "123456789"
                repository.updateReceiptStatusOfTransaksjon(dto.transaksjonID, nyKvitteringStatus, nyNavTrekkId)
                val transaksjonTilOs = repository.getTransaksjonTilOs(dto.transaksjonID)
                transaksjonTilOs.shouldNotBeNull()

                Then("Skal transaksjonen oppdateres med ny transaksjonstatus") {

                    transaksjonTilOs.kvitteringStatus shouldBe nyKvitteringStatus
                }
                And("Tidspunktsistestatus skal være nå") {
                    val now = dbNow()
                    transaksjonTilOs.tidspunktSisteStatus?.shouldBeIn(now.minusSeconds(10)..now)
                }
                And("Navtrekkid skal ikke være blank") {
                    transaksjonTilOs.navTrekkId shouldBe nyNavTrekkId
                }
            }
            When("transaksjonstatus er SENDT og kvitteringsstatus er IKKE_MOTTAT") {
                repository.updateTransaksjonSendt(dto.transaksjonID)
                Then("getTransakjonerTilOsSomManglerKvittering skal ikke være null") {
                    val transaksjonTilOs = repository.getTransakjonerTilOsSomManglerKvittering()
                    transaksjonTilOs.shouldNotBeNull()
                }
            }
        }

        Given("To trekk lagres") {
            DBListener.clearDB()
            val sekvensNummer = repository.getLastSekvensnummer()
            sekvensNummer shouldBe 0
            val trekkpaalegg =
                jsonConfig.decodeFromString<List<Trekkpaalegg>>(resourceToString("FraSkatt_Trekkversjon1_1Trekkalternativ-2trekk.json"))
            trekkpaalegg.forEach { repository.insertTrekkFraSkatt(it) }
            Then("Skal høyeste sekvensnummer være lik høyeste sekvensnummer i data fra skatt") {
                val newMaxSekvensNummer = repository.getLastSekvensnummer()

                newMaxSekvensNummer shouldNotBe 0
                newMaxSekvensNummer shouldBe trekkpaalegg.maxOf { it.sekvensnummer }
            }
        }

        Given("Det finnes eksisterende trekk") {
            DBListener.clearDB()
            doesTrekkExist("1", 1) shouldBe false
            val trekkpalegg =
                jsonConfig.decodeFromString<List<Trekkpaalegg>>(resourceToString("FraSkatt_Trekkversjon1_1Trekkalternativ-2trekk.json"))
            trekkpalegg.forEach { repository.insertTrekkFraSkatt(it) }

            repository.getLastSekvensnummer() shouldBe trekkpalegg.maxOf { it.sekvensnummer }

            val allTrekkFraSkatt = repository.getAllTrekkFraSkatt()
            allTrekkFraSkatt.shouldNotBeEmpty()
            doesTrekkExist("1", 1) shouldBe true
            doesTrekkExist("2_xx", 1) shouldBe true
            doesTrekkExist("1", 2) shouldBe false
        }

        Given("Det kommer inn trekk ut av rekkefølge") {
            DBListener.clearDB()
            val trekkpaalegg = jsonConfig.decodeFromString<List<Trekkpaalegg>>(resourceToString("diverse_trekk_ut_av_sekvens.json"))
            trekkpaalegg.forEach { repository.insertTrekkFraSkatt(it) }

            Then("Kommer i riktig rekkefølge når de hentes fra DB") {
                val trekkNotSent: List<TrekkFraSkatt> = repository.getTrekkFraSkattMedStatus(MOTTATT)
                // Alle trekk skal være i rekkefølge
                trekkNotSent.map { it.sekvensnummer }.zipWithNext().all { (a, b) -> a < b } shouldBe true
            }
        }

        Given("To trekk finnes med status MOTTATT") {
            DBListener.clearDB()
            doesTrekkExist("1", 1) shouldBe false
            val trekkpalegg =
                jsonConfig.decodeFromString<List<Trekkpaalegg>>(resourceToString("FraSkatt_Trekkversjon1_1Trekkalternativ-2trekk.json"))
            trekkpalegg.forEach { repository.insertTrekkFraSkatt(it) }

            val trekkMottatt = repository.getTrekkFraSkattMedStatus(MOTTATT)

            trekkMottatt.shouldHaveSize(2)
            val ettTrekk = trekkMottatt.first()

            When("Et trekk endrer status til BEHANDLET") {
                repository.updateTrekkFraSkattStatus(ettTrekk.id, BEHANDLET)
                val behandlet = repository.getTrekkFraSkattMedStatus(BEHANDLET)
                behandlet.shouldHaveSize(1)
                behandlet.first().id shouldBe ettTrekk.id

                Then("Dukker det ikke opp blandt MOTTATTE trekk") {
                    val mottatt = repository.getTrekkFraSkattMedStatus(MOTTATT)
                    mottatt.shouldHaveSize(1)
                    mottatt.first().id shouldNotBe ettTrekk.id
                }
            }
        }

        Given("Det finnes transaksjoner i databasen") {
            DBListener.clearDB()
            DBListener.clearDB()
            val trekkId = "TestTrekkID"
            val transaksjonsId = "123id"

            val transaksjonsId1 = "$transaksjonsId-1"
            val transaksjonsId2 = "$transaksjonsId-2"
            val transaksjonsId3 = "$transaksjonsId-3"
            val dokument1 = lagDokumentTilOppdrag(transaksjonsId1)
            val dokument2 = lagDokumentTilOppdrag(transaksjonsId2)
            val dokument3 = lagDokumentTilOppdrag(transaksjonsId3)

            val dto1 =
                OSDto(
                    transaksjonID = transaksjonsId1,
                    trekkId,
                    trekkversjon = 1,
                    dokument1.innrapporteringTrekk,
                    jsonConfig.encodeToString<DokumentTilOppdrag>(dokument1),
                )
            val dto2 =
                OSDto(
                    transaksjonID = transaksjonsId2,
                    trekkId,
                    trekkversjon = 1,
                    dokument2.innrapporteringTrekk,
                    jsonConfig.encodeToString<DokumentTilOppdrag>(dokument2),
                )

            val dto3 =
                OSDto(
                    transaksjonID = transaksjonsId3,
                    trekkId,
                    trekkversjon = 1,
                    dokument3.innrapporteringTrekk,
                    jsonConfig.encodeToString<DokumentTilOppdrag>(dokument3),
                )
            repository.insertTransaksjonTilOs(dto1)
            repository.insertTransaksjonTilOs(dto2)
            repository.insertTransaksjonTilOs(dto3)

            When("Transaksjoner hentes for TrekkID: $trekkId") {
                val transaksjoner = repository.getTransaksjonerTilOsForTrekkID(trekkId)

                Then("Skal transaksjonen som hentes matche transaksjonen som ble lagret") {
                    transaksjoner.shouldHaveSize(3)
                    val transaksjon = transaksjoner.first { it.transaksjonsID == transaksjonsId1 }
                    transaksjon.trekkIdSke shouldBe trekkId
                    transaksjon.transaksjonsID shouldBe dto1.transaksjonID
                    transaksjon.kvitteringStatus shouldBe KvitteringStatus.IKKE_MOTTATT
                    transaksjon.transaksjonStatus shouldBe TransaksjonsStatus.IKKE_SENDT
                    transaksjon.navTrekkId shouldBe ""
                    transaksjon.tidspunktSendt shouldBe null
                    transaksjon.tidspunktSisteStatus?.toLocalDate() shouldBe LocalDate.now()
                    with(dokument1.innrapporteringTrekk) {
                        transaksjon.trekkAlternativ shouldBe kodeTrekkAlternativ
                        transaksjon.trekktype shouldBe kodeTrekktype
                        transaksjon.debitorId shouldBe debitorId
                        transaksjon.kreditorsref shouldBe kreditorsRef
                        transaksjon.aksjonskode shouldBe aksjonskode
                        transaksjon.kreditorTrekkId shouldBe kreditorTrekkId
                        transaksjon.kreditorIdTss shouldBe kreditorIdTss
                        transaksjon.saldo shouldBe saldo
                        transaksjon.kilde shouldBe kilde
                        transaksjon.kid shouldBe kid
                    }
                }
            }

            When("Vi oppdaterer en transaksjonstatus") {
                repository.updateTransaksjonSendt(transaksjonsId3)

                Then("Skal ny status settes korrekt") {
                    val transaksjoner = repository.getTransaksjonerTilOsForTrekkID(trekkId)
                    transaksjoner.filter { it.transaksjonsID == transaksjonsId3 }.shouldHaveSize(1)
                    transaksjoner.filter { it.transaksjonStatus == TransaksjonsStatus.SENDT }.shouldHaveSize(1)
                    transaksjoner.filter { it.transaksjonStatus == TransaksjonsStatus.IKKE_SENDT }.shouldHaveSize(2)
                }
            }
            When("Vi henter transaksjoner som ikke er sendt til OS") {
                Then("Skal transaksjonene ha transaksjon status null eller ${TransaksjonsStatus.IKKE_SENDT}") {
                    val transaksjonerIkkeSendt = repository.getTransaksjonerTilOsSomIkkeErSendt()

                    transaksjonerIkkeSendt.shouldHaveSize(2)
                    transaksjonerIkkeSendt.filter { it.transaksjonStatus == TransaksjonsStatus.SENDT }.shouldHaveSize(0)
                    transaksjonerIkkeSendt.filter { it.transaksjonStatus == TransaksjonsStatus.IKKE_SENDT }.shouldHaveSize(2)
                }
            }
        }

        Given("Vi henter unike TrekkAlternativ for en spesifikk TrekkID") {
            DBListener.clearDB()
            val trekkIdSke = "TestTrekkID"
            val transaksjonsId1 = "id1"
            val transaksjonsId2 = "id2"
            // Insert transactions with distinct TrekkAlternativ
            val dto1 =
                OSDto(
                    transaksjonID = transaksjonsId1,
                    trekkIdSke,
                    trekkversjon = 1,
                    lagDokumentTilOppdrag(transaksjonsId1).copy(innrapporteringTrekk = dummyInnrapporteringTrekk.copy(kodeTrekkAlternativ = TrekkAlternativ.LOPM)).innrapporteringTrekk,
                    "",
                )
            val dto2 =
                OSDto(
                    transaksjonID = transaksjonsId2,
                    trekkIdSke,
                    trekkversjon = 1,
                    lagDokumentTilOppdrag(transaksjonsId2).copy(innrapporteringTrekk = dummyInnrapporteringTrekk.copy(kodeTrekkAlternativ = TrekkAlternativ.LOPP)).innrapporteringTrekk,
                    "",
                )
            repository.insertTransaksjonTilOs(dto1)
            repository.insertTransaksjonTilOs(dto2)

            When("Når unike TrekkAlternativ verdier hentes for for TrekkID: $trekkIdSke") {
                val alternativ = repository.getTrekkAlternativOS(trekkIdSke)

                Then("Så skal verdiene inneholde kun én LOPM og kun én LOPP") {
                    alternativ.shouldContainExactlyInAnyOrder(TrekkAlternativ.LOPM, TrekkAlternativ.LOPP)
                }
            }
        }

        Given("Vi henter perioder for en spesifikk TrekkID og TrekkAlternativ") {
            DBListener.clearDB()
            val trekkIdSke = "TestTrekkID"
            val alternativ = TrekkAlternativ.LOPM
            val transaksjosID = "id1"

            val perioderTilOS =
                PeriodeTilOS(
                    sats = 5000.0,
                    periodeFomDato = "2024-01-01",
                    periodeTomDato = "2024-12-31",
                )

            val dto =
                OSDto(
                    transaksjonID = transaksjosID,
                    trekkIdSke,
                    trekkversjon = 1,
                    lagDokumentTilOppdrag(transaksjosID)
                        .copy(
                            innrapporteringTrekk =
                                dummyInnrapporteringTrekk.copy(
                                    kodeTrekkAlternativ = alternativ,
                                    perioder = Perioder(listOf(perioderTilOS.asPeriode())),
                                ),
                        ).innrapporteringTrekk,
                    "",
                )

            repository.insertTransaksjonTilOs(dto)

            When("Vi henter perioder for TrekkID '$trekkIdSke' og TrekkAlternativ '$alternativ'") {
                val fetchedPerioder = repository.getPerioderTilOs(trekkIdSke, alternativ)

                Then("Skal periodene som hentes matche periodene som ble lagret") {
                    fetchedPerioder.shouldHaveSize(1)
                    val retrieved = fetchedPerioder.first()
                    retrieved.sats shouldBe perioderTilOS.sats

                    retrieved.periodeFomDato shouldBe perioderTilOS.periodeFomDato
                    retrieved.periodeTomDato shouldBe perioderTilOS.periodeTomDato
                }
            }
        }

        Given("Vi henter betalingsinformasjon for en spesifikk TrekkID") {
            DBListener.clearDB()
            val trekkId = 1L
            val trekkpaalegg1 = jsonConfig.decodeFromString<List<Trekkpaalegg>>(resourceToString("InitTrekk/Fra_Skatt_Trekk1_versjon1_en_periode_belop.json")).first()
            repository.insertTrekkFraSkatt(trekkpaalegg1) ?: 0L

            val sampleBetalingsinformasjon =
                Betalingsinformasjon(
                    betalingsmottaker = "971648199",
                    kidnummer = "17654202404",
                    kontonummer = "70213997155",
                )

            When("Betalingsinformasjon hentes for TrekkID: $trekkId") {
                val betalingsinformasjon = repository.getBetalingsinformasjonForTrekk(trekkId)

                Then("Skal betalingsinformasjonen som hentes matche den som ble lagret") {
                    betalingsinformasjon.shouldNotBeNull()
                    betalingsinformasjon.betalingsmottaker shouldBe sampleBetalingsinformasjon.betalingsmottaker
                    betalingsinformasjon.kidnummer shouldBe sampleBetalingsinformasjon.kidnummer
                    betalingsinformasjon.kontonummer shouldBe sampleBetalingsinformasjon.kontonummer
                }
            }
        }
        Given("Vi har trekk med forskjellige statuser i databasen") {
            DBListener.clearDB()
            repository.insertRawSQL(resourceToString("dbTestData/RepositoryTest/trekkMedForskjelligeStatuser.sql"))

            When("Når listen over trekk som skal prosesseres hentes") {
                Then("Skal bare trekk med status MOTTATT eller REPETERES være i listen") {
                    val trekkId = repository.getTrekkIdTilTrekkSomSkalBehandles()
                    trekkId.size shouldBe 2
                    trekkId.forEach { id ->
                        val status = repository.getTrekkFraSkattStatus(id)
                        status shouldBeIn listOf(SkattTrekkStatus.MOTTATT, SkattTrekkStatus.REPETERES)
                    }
                }
            }
        }
    })

private fun dbNow(): LocalDateTime =
    DBListener.dataSource.withTransaction { session ->
        session.single(queryOf("select now() as tidspunkt")) { row -> row.localDateTime("tidspunkt") }!!
    }

private fun compareTrekk(trekkpaalegg: Trekkpaalegg, lagret: TrekkFraSkatt) {
    lagret.trekkstatus shouldBe trekkpaalegg.trekkstatus.name
    lagret.trekkid shouldBe trekkpaalegg.trekkid
    lagret.saksnummer shouldBe trekkpaalegg.saksnummer
    lagret.skyldner shouldBe trekkpaalegg.skyldner
    lagret.trekkversjon shouldBe trekkpaalegg.trekkversjon
    lagret.saksnummer shouldBe trekkpaalegg.saksnummer
    lagret.skyldner shouldBe trekkpaalegg.skyldner
    lagret.trekkpliktig shouldBe trekkpaalegg.trekkpliktig
}

private fun doesTrekkExist(trekkId: String, trekkversjon: Int): Boolean = repository.doesTrekkExist(trekkId, trekkversjon)

private fun getTrekkFraSkatt(id: Long): TrekkFraSkatt = repository.getTrekkFraSkatt(id)!!

private fun compareBetalingsinformasjon(betalingsinformasjon: Betalingsinformasjon, lagret: BetalingsinformasjonFraSkatt) {
    lagret.betalingsmottaker shouldBe betalingsinformasjon.betalingsmottaker
    lagret.kidnummer shouldBe betalingsinformasjon.kidnummer
    lagret.kontonummer shouldBe betalingsinformasjon.kontonummer
}

private fun comparePeriode(trekkstorrelseForPeriode: TrekkstorrelseForPeriode, lagret: PeriodeFraSkatt) {
    lagret.startdato shouldBe trekkstorrelseForPeriode.startdato
    lagret.sluttdato shouldBe trekkstorrelseForPeriode.sluttdato
    lagret.trekkbeloep shouldBe trekkstorrelseForPeriode.trekkbeloep?.trekkbeloep
    lagret.trekkprosent shouldBe trekkstorrelseForPeriode.trekkprosent?.trekkprosent
}

private fun comparePerioder(trekkstorrelseForPeriode: List<TrekkstorrelseForPeriode>, lagret: List<PeriodeFraSkatt>) {
    lagret.size shouldBe trekkstorrelseForPeriode.size
    trekkstorrelseForPeriode.forEach { periode ->
        val lagretPeriode = lagret.find { it.startdato == periode.startdato }
        lagretPeriode.shouldNotBeNull()
        comparePeriode(periode, lagretPeriode)
    }
}

private fun compareFeilmelding(kvittering: KvitteringFraOppdrag, feilmelding: Feilmelding) {
    feilmelding.id shouldBe 1L
    feilmelding.kreditorTrekkId shouldBe kvittering.dokument.innrapporteringTrekk.kreditorTrekkId
    feilmelding.trekkAlternativ shouldBe kvittering.dokument.innrapporteringTrekk.kodeTrekkAlternativ
    feilmelding.transaksjonsId shouldBe kvittering.dokument.transaksjonsId
    feilmelding.feilkode shouldBe kvittering.mmel!!.kodeMelding
    feilmelding.beskrivelse shouldBe kvittering.mmel.beskrMelding
}