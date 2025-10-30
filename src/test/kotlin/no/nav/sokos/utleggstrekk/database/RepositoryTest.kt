package no.nav.sokos.utleggstrekk.database

import java.time.LocalDateTime

import kotlin.time.Duration.Companion.seconds

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.ranges.shouldBeIn
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.mockk

import no.nav.sokos.utleggstrekk.config.jsonConfig
import no.nav.sokos.utleggstrekk.database.model.BetalingsinformasjonFraSkatt
import no.nav.sokos.utleggstrekk.database.model.Feilmelding
import no.nav.sokos.utleggstrekk.database.model.KvitteringStatus
import no.nav.sokos.utleggstrekk.database.model.PeriodeFraSkatt
import no.nav.sokos.utleggstrekk.database.model.SkattTrekkStatus.BEHANDLET
import no.nav.sokos.utleggstrekk.database.model.SkattTrekkStatus.MOTTATT
import no.nav.sokos.utleggstrekk.database.model.TransaksjonsStatus
import no.nav.sokos.utleggstrekk.database.model.TrekkFraSkatt
import no.nav.sokos.utleggstrekk.domene.nav.Aksjonskode
import no.nav.sokos.utleggstrekk.domene.nav.DokumentTilOppdrag
import no.nav.sokos.utleggstrekk.domene.nav.KvitteringFraOppdrag
import no.nav.sokos.utleggstrekk.domene.nav.OSDto
import no.nav.sokos.utleggstrekk.domene.nav.TrekkAlternativ
import no.nav.sokos.utleggstrekk.domene.ske.Betalingsinformasjon
import no.nav.sokos.utleggstrekk.domene.ske.Trekkpaalegg
import no.nav.sokos.utleggstrekk.domene.ske.TrekkstorrelseForPeriode
import no.nav.sokos.utleggstrekk.listener.DBListener
import no.nav.sokos.utleggstrekk.service.withTransaction
import no.nav.sokos.utleggstrekk.util.resourceToString

class RepositoryTest :
    BehaviorSpec({
        extensions(DBListener)

        Given("Vi henter trekk som ikke er sendt") {
            val skalSendes = jsonConfig.decodeFromString<List<Trekkpaalegg>>(resourceToString("InitTrekk/Fra_Skatt_Trekk1_versjon1_en_periode_belop.json")).first()
            saveTrekkpaalegg(skalSendes)
            val dto =
                OSDto(
                    transaksjonsID = "SkalSendes",
                    skalSendes.trekkid,
                    mockk<DokumentTilOppdrag>(), // TODO: Legge inn faktisk dokument og lagre det som string i DB?
                )
            DBListener.dataSource.withTransaction { session ->
                RepositoryNy.insertTransaksjonTilOs(dto, session)
            }

            saveTrekkpaalegg(skalSendes.copy(trekkid = "SkalOgsåSendes"))

            val trekkSomErSendt = skalSendes.copy(trekkid = "SkalIkkeSendes")
            saveTrekkpaalegg(skalSendes.copy(trekkid = "SkalIkkeSendes"))
            val dtoSomErSendt =
                OSDto(
                    transaksjonsID = "SkalIkkeSendes",
                    trekkSomErSendt.trekkid,
                    mockk<DokumentTilOppdrag>(), // TODO: Legge inn faktisk dokument og lagre det som string i DB?
                )
            DBListener.dataSource.withTransaction { session ->
                RepositoryNy.insertTransaksjonTilOs(dtoSomErSendt, session)
                RepositoryNy.updateTransaksjonStatus(dtoSomErSendt.transaksjonsID, TransaksjonsStatus.SENDT, session)
            }

            val ikkeSendt =
                DBListener.dataSource.withTransaction { session ->
                    RepositoryNy.getTrekkSomIkkeErSendt(session)
                }

            ikkeSendt.shouldHaveSize(2)
        }

        Given("Vi henter perioder for trekk med én versjon") {
            DBListener.clearDB()
            val trekkpaalegg1 = jsonConfig.decodeFromString<List<Trekkpaalegg>>(resourceToString("InitTrekk/Fra_Skatt_Trekk1_versjon1_en_periode_belop.json")).first()
            val idtrekkpaalegg1 = saveTrekkpaalegg(trekkpaalegg1)
            val trekkIdSke = trekkpaalegg1.trekkid

            idtrekkpaalegg1.shouldNotBeNull()
            val perioder: List<PeriodeFraSkatt> =
                DBListener.dataSource.withTransaction { session ->
                    RepositoryNy.getAllePerioderForTrekkId(trekkIdSke, session)
                }

            perioder.shouldHaveSize(1)

            val trekkpaalegg2 = jsonConfig.decodeFromString<List<Trekkpaalegg>>(resourceToString("InitTrekk/Fra_Skatt_Trekk2_versjon1_to_perioder_belop.json")).first()
            val idtrekkpaalegg2 = saveTrekkpaalegg(trekkpaalegg2)
            val trekkIdSke2 = trekkpaalegg2.trekkid
            idtrekkpaalegg2.shouldNotBeNull()

            val perioder2: List<PeriodeFraSkatt> =
                DBListener.dataSource.withTransaction { session ->
                    RepositoryNy.getAllePerioderForTrekkId(trekkIdSke2, session)
                }
            perioder2.shouldHaveSize(3)
        }

        // TODO rydd opp :)
        Given("Vi henter perioder for trekk med flere versjoner") {
            DBListener.clearDB()
            val trekkpaalegg1 = jsonConfig.decodeFromString<List<Trekkpaalegg>>(resourceToString("InitTrekk/Fra_Skatt_Trekk1_versjon1_en_periode_belop.json")).first()

            val idtrekkpaalegg1: Long? = saveTrekkpaalegg(trekkpaalegg1)
            idtrekkpaalegg1.shouldNotBeNull()

            val trekkpaalegg2 = jsonConfig.decodeFromString<List<Trekkpaalegg>>(resourceToString("InitTrekk/Fra_skatt_Trekk1_versjon2_endret_belop.json")).first()
            val idtrekkpaalegg2 = saveTrekkpaalegg(trekkpaalegg2)
            idtrekkpaalegg2.shouldNotBeNull()

            When("getPerioderForTrekkVersjon") {

                Then("Skal perioder hentes kun for trekk med gitt versjon, sekvensnummer og trekkid") {
                    val perioderVersjon1 =
                        DBListener.dataSource.withTransaction { session ->
                            RepositoryNy.getPerioderForTrekkVersjon(idtrekkpaalegg1, trekkpaalegg1.sekvensnummer, trekkpaalegg1.trekkversjon, session)
                        }

                    perioderVersjon1.shouldHaveSize(1)
                    val perioderVersjon2 =
                        DBListener.dataSource.withTransaction { session ->
                            RepositoryNy.getPerioderForTrekkVersjon(idtrekkpaalegg2, trekkpaalegg2.sekvensnummer, trekkpaalegg2.trekkversjon, session)
                        }
                    perioderVersjon2.shouldHaveSize(2)
                }
            }

            When("getPerioderForTrekkId") {
                Then("Skal alle perioder for det trekket hentes") {
                    val perioder = DBListener.dataSource.withTransaction { session -> RepositoryNy.getAllePerioderForTrekkId(trekkpaalegg1.trekkid, session) }
                    perioder.shouldHaveSize(3)
                }
            }
        }

        Given("Data fra skatt skal lagres") {
            DBListener.clearDB()
            val trekkpaalegg = jsonConfig.decodeFromString<List<Trekkpaalegg>>(resourceToString("InitTrekk/Fra_Skatt_Trekk1_versjon1_en_periode_belop.json")).first()

            When("Trekkpålegg lagres") {
                val id = saveTrekkpaalegg(trekkpaalegg)
                id.shouldNotBeNull()

                eventually(duration = 1.seconds) {
                    val lagretTrekk = getTrekkFraSkatt(trekkpaalegg.trekkid)
                    lagretTrekk.shouldNotBeNull()
                    Then("Skal Trekkpålegg lagres i tabellen 'fraskatt'") {

                        compareTrekk(trekkpaalegg, lagretTrekk)
                    }
                    And("TrekkstørrelseForPeriode skal lagres i tabellen 'periOde'") {
                        val lagretPeriode = getPerioderForTrekk(lagretTrekk.trekkid)
                        comparePerioder(trekkpaalegg.trekkstoerrelseForPeriode, lagretPeriode)
                    }
                    And("Betalingsinformasjon skal lagres i tabellen 'betalingsinformasjonfraskatt'") {
                        val lagretBetalingsinformasjon = getBetalingsinformasjonForTrekk(id)
                        lagretBetalingsinformasjon.shouldNotBeNull()
                        compareBetalingsinformasjon(trekkpaalegg.betalingsinformasjon, lagretBetalingsinformasjon)
                    }
                }
            }
        }

        Given("Feilmelding skal lagres") {
            val kvittering = jsonConfig.decodeFromString<KvitteringFraOppdrag>(resourceToString("kvittering-feil.json"))
            DBListener.dataSource.withTransaction { session -> RepositoryNy.insertFeilmeldingFraOS(kvittering, session) }

            Then("Skal trekkid, trekkalternativ, corrid, feilkode og beskrivelse lagres") {
                val feilmelding =
                    DBListener.dataSource.withTransaction { session ->
                        RepositoryNy.getFeilmeldingerFraOS(
                            kvittering.dokument.transaksjonsId,
                            session,
                        )
                    }
                feilmelding.shouldNotBeNull()
                compareFeilmelding(kvittering, feilmelding)
            }
        }
        Given("Transaksjon til OS skal lagres") {
            val dto =
                OSDto(
                    transaksjonsID = "123id",
                    "Et trekk",
                    mockk<DokumentTilOppdrag>(),
                )
            DBListener.dataSource.withTransaction { session ->
                RepositoryNy.insertTransaksjonTilOs(dto, session)
            }
            When("Transaksjon er lagret i tabellen 'transaksjon_os'") {
                val transaksjonTilOs =
                    DBListener.dataSource.withTransaction { session ->
                        RepositoryNy.getTransaksjonTilOs(dto.transaksjonsID, session)
                    }
                transaksjonTilOs.shouldNotBeNull()

                Then("Skal transaksjonstatus skal være TransaksjonsStatus.IKKE_SENDT") {
                    transaksjonTilOs.transaksjonStatus shouldBe TransaksjonsStatus.IKKE_SENDT
                }
                And("KvitteringStatus skal være KvitteringStatus.IKKE_MOTTATT") {
                    transaksjonTilOs.kvitteringStatus shouldBe KvitteringStatus.IKKE_MOTTATT
                }
                And("Tidspunktsendt skal være nå") {
                    val now = LocalDateTime.now()
                    transaksjonTilOs.tidspunktSendt.shouldBeIn(now.minusSeconds(10)..now)
                }
                And("Tidspunktsistestatus skal være nå") {
                    val now = LocalDateTime.now()
                    transaksjonTilOs.tidspunktSisteStatus.shouldBeIn(now.minusSeconds(10)..now)
                }
                And("Enumverdier skal lagres korrekt") {
                    transaksjonTilOs.aksjonskode shouldBe Aksjonskode.NY
                    transaksjonTilOs.trekkAlternativ shouldBe TrekkAlternativ.LOPP
                }
            }

            When("Transaksjonstatus oppdateres") {
                DBListener.dataSource.withTransaction { session ->
                    RepositoryNy.updateTransaksjonStatus(dto.transaksjonsID, TransaksjonsStatus.SENDT, session)
                }
                val transaksjonTilOs =
                    DBListener.dataSource.withTransaction { session ->
                        RepositoryNy.getTransaksjonTilOs(dto.transaksjonsID, session)
                    }
                transaksjonTilOs.shouldNotBeNull()
                Then("Skal transaksjonen oppdateres med ny transaksjonstatus") {

                    transaksjonTilOs.transaksjonStatus shouldBe TransaksjonsStatus.SENDT
                }
                And("Tidspunktsistestatus skal være nå") {
                    val now = LocalDateTime.now()
                    transaksjonTilOs.tidspunktSisteStatus.shouldBeIn(now.minusSeconds(10)..now)
                }
            }
            When("Transaksjon oppdateres med kvitteringsstatus og navtrekkid") {
                val nyKvitteringStatus = KvitteringStatus.OK
                val nyNavTrekkId = "123456789"
                DBListener.dataSource.withTransaction { session ->
                    RepositoryNy.updateTransaksjon(dto.transaksjonsID, nyKvitteringStatus, nyNavTrekkId, session)
                }
                val transaksjonTilOs =
                    DBListener.dataSource.withTransaction { session ->
                        RepositoryNy.getTransaksjonTilOs(dto.transaksjonsID, session)
                    }
                transaksjonTilOs.shouldNotBeNull()
                Then("Skal transaksjonen oppdateres med ny transaksjonstatus") {

                    transaksjonTilOs.kvitteringStatus shouldBe nyKvitteringStatus
                }
                And("Tidspunktsistestatus skal være nå") {
                    val now = LocalDateTime.now()
                    transaksjonTilOs.tidspunktSisteStatus.shouldBeIn(now.minusSeconds(10)..now)
                }
                And("Navtrekkid skal ikke være blank") {
                    transaksjonTilOs.navTrekkId shouldBe nyNavTrekkId
                }
            }
        }

        Given("To trekk lagres") {
            DBListener.clearDB()
            val sekvensNummer = getMaxSekvensnummer()
            sekvensNummer shouldBe 0
            val trekkpaalegg =
                jsonConfig.decodeFromString<List<Trekkpaalegg>>(resourceToString("FraSkatt_Trekkversjon1_1Trekkalternativ-2trekk.json"))
            trekkpaalegg.forEach { saveTrekkpaalegg(it) }
            Then("Skal høyeste sekvensnummer være lik høyeste sekvensnummer i data fra skatt") {
                val newMaxSekvensNummer = getMaxSekvensnummer()

                newMaxSekvensNummer shouldNotBe 0
                newMaxSekvensNummer shouldBe trekkpaalegg.maxOf { it.sekvensnummer }
            }
        }

        Given("Det finnes eksisterende trekk") {
            DBListener.clearDB()
            doesTrekkExist("1", 1) shouldBe false
            val trekkpalegg =
                jsonConfig.decodeFromString<List<Trekkpaalegg>>(resourceToString("FraSkatt_Trekkversjon1_1Trekkalternativ-2trekk.json"))
            trekkpalegg.forEach { saveTrekkpaalegg(it) }

            getMaxSekvensnummer() shouldBe trekkpalegg.maxOf { it.sekvensnummer }

            val allTrekkFraSkatt = getAllTrekkFraSkatt()
            allTrekkFraSkatt.shouldNotBeEmpty()
            doesTrekkExist("1", 1) shouldBe true
            doesTrekkExist("2_xx", 2) shouldBe true
            doesTrekkExist("1", 2) shouldBe false
        }

        Given("Det kommer inn trekk ut av rekkefølge") {
            DBListener.clearDB()
            val trekkpaalegg = jsonConfig.decodeFromString<List<Trekkpaalegg>>(resourceToString("diverse_trekk_ut_av_sekvens.json"))
            trekkpaalegg.forEach { saveTrekkpaalegg(it) }

            Then("Kommer i riktig rekkefølge når de hentes fra DB") {
                val trekkNotSent: List<TrekkFraSkatt> =
                    DBListener.dataSource.withTransaction { session ->
                        RepositoryNy.getTrekkFraSkattMedStatus(MOTTATT, session)
                    }
                // Alle trekk skal være i rekkefølge
                trekkNotSent.map { it.sekvensnummer }.zipWithNext().all { (a, b) -> a < b } shouldBe true
            }
        }

        Given("To trekk finnes med status MOTTATT") {
            DBListener.clearDB()
            doesTrekkExist("1", 1) shouldBe false
            val trekkpalegg =
                jsonConfig.decodeFromString<List<Trekkpaalegg>>(resourceToString("FraSkatt_Trekkversjon1_1Trekkalternativ-2trekk.json"))
            trekkpalegg.forEach { saveTrekkpaalegg(it) }

            val trekkMottatt = DBListener.dataSource.withTransaction { session -> RepositoryNy.getTrekkFraSkattMedStatus(MOTTATT, session) }

            trekkMottatt.shouldHaveSize(2)
            val ettTrekk = trekkMottatt.first()

            When("Et trekk endrer status til BEHANDLET") {
                DBListener.dataSource.withTransaction { session -> RepositoryNy.setStatus(ettTrekk, BEHANDLET, session) }
                val behandlet = DBListener.dataSource.withTransaction { session -> RepositoryNy.getTrekkFraSkattMedStatus(BEHANDLET, session) }
                behandlet.shouldHaveSize(1)
                behandlet.first().id shouldBe ettTrekk.id

                Then("Dukker det ikke opp blandt MOTTATTE trekk") {
                    val mottatt = DBListener.dataSource.withTransaction { session -> RepositoryNy.getTrekkFraSkattMedStatus(MOTTATT, session) }
                    mottatt.shouldHaveSize(1)
                    mottatt.first().id shouldNotBe ettTrekk.id
                }
            }
        }
    })

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

private fun doesTrekkExist(trekkId: String, trekkversjon: Int): Boolean =
    DBListener.dataSource.withTransaction { session ->
        RepositoryNy.doesTrekkExist(trekkId, trekkversjon, session)
    }

private fun saveTrekkpaalegg(trekkpalegg: Trekkpaalegg): Long? =
    DBListener.dataSource.withTransaction { session ->
        RepositoryNy.insertTrekkFraSkatt(trekkpalegg, session)
    }

private fun getMaxSekvensnummer(): Int =
    DBListener.dataSource.withTransaction { session ->
        RepositoryNy.getLastSekvensnummer(session)
    }

private fun getTrekkFraSkatt(id: String): TrekkFraSkatt? =
    DBListener.dataSource.withTransaction { session ->
        RepositoryNy.getTrekkFraSkatt(id, session).first()
    }

private fun getAllTrekkFraSkatt(): List<TrekkFraSkatt> =
    DBListener.dataSource.withTransaction { session ->
        RepositoryNy.getAllTrekkFraSkatt(session)
    }

private fun getPerioderForTrekk(trekkId: String): List<PeriodeFraSkatt> =
    DBListener.dataSource.withTransaction { session ->
        RepositoryNy.getAllePerioderForTrekkId(trekkId, session)
    }

private fun getBetalingsinformasjonForTrekk(trekkId: Long): BetalingsinformasjonFraSkatt? =
    DBListener.dataSource.withTransaction { session ->
        RepositoryNy.getBetalingsinformasjonForTrekk(trekkId, session)
    }

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