package no.nav.sokos.utleggstrekk.database

import kotlin.time.ExperimentalTime
import kotlinx.serialization.json.Json

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import no.nav.sokos.utleggstrekk.database.model.BetalingsinformasjonFraSkatt
import no.nav.sokos.utleggstrekk.database.model.Feilmelding
import no.nav.sokos.utleggstrekk.database.model.Periode
import no.nav.sokos.utleggstrekk.database.model.TrekkFraSkatt
import no.nav.sokos.utleggstrekk.domene.nav.KvitteringFraOppdrag
import no.nav.sokos.utleggstrekk.domene.ske.Betalingsinformasjon
import no.nav.sokos.utleggstrekk.domene.ske.Trekkpaalegg
import no.nav.sokos.utleggstrekk.domene.ske.TrekkstorrelseForPeriode
import no.nav.sokos.utleggstrekk.listener.DBListener
import no.nav.sokos.utleggstrekk.service.withTransaction
import no.nav.sokos.utleggstrekk.util.resourceToString

@OptIn(ExperimentalTime::class)
class RepositoryTest :
    BehaviorSpec({

        extensions(DBListener)
        //   val dataSource = testContainer.dataSource
        //   val repository = Repository(testContainer.dataSource)
        val repositoryNy = RepositoryNy()
        val json =
            Json {
                prettyPrint = true
                isLenient = true
                explicitNulls = false
            }

        fun saveTrekkpaalegg(trekkpalegg: Trekkpaalegg): Long? =
            DBListener.dataSource.withTransaction { session ->
                repositoryNy.insertTrekkFraSkatt(trekkpalegg, session)
            }

        fun getUtleggsTrekk(id: Long): TrekkFraSkatt? =
            DBListener.dataSource.withTransaction { session ->
                repositoryNy.getTrekkFraSkatt(id, session)
            }

        fun getPerioderForTrekk(trekkId: Long): List<Periode> =
            DBListener.dataSource.withTransaction { session ->
                repositoryNy.getPerioderForTrekk(trekkId, session)
            }

        fun getBetalingsinformasjonForTrekk(trekkId: Long): BetalingsinformasjonFraSkatt? =
            DBListener.dataSource.withTransaction { session ->
                repositoryNy.getBetalingsinformasjonForTrekk(trekkId, session)
            }

        fun compareBetalingsinformasjon(betalingsinformasjon: Betalingsinformasjon, lagret: BetalingsinformasjonFraSkatt) {
            lagret.betalingsmottaker shouldBe betalingsinformasjon.betalingsmottaker
            lagret.kidnummer shouldBe betalingsinformasjon.kidnummer
            lagret.kontonummer shouldBe betalingsinformasjon.kontonummer
        }

        fun comparePeriode(trekkstorrelseForPeriode: TrekkstorrelseForPeriode, lagret: Periode) {
            lagret.startdato shouldBe trekkstorrelseForPeriode.startdato
            lagret.sluttdato shouldBe trekkstorrelseForPeriode.sluttdato
            lagret.trekkbeloep shouldBe trekkstorrelseForPeriode.trekkbeloep?.trekkbeloep
            lagret.trekkprosent shouldBe trekkstorrelseForPeriode.trekkprosent?.trekkprosent
        }

        fun comparePerioder(trekkstorrelseForPeriode: List<TrekkstorrelseForPeriode>, lagret: List<Periode>) {
            lagret.size shouldBe trekkstorrelseForPeriode.size
            trekkstorrelseForPeriode.forEach { periode ->
                val lagretPeriode = lagret.find { it.startdato == periode.startdato }
                lagretPeriode.shouldNotBeNull()
                comparePeriode(periode, lagretPeriode)
            }
        }

        fun compareFeilmelding(kvittering: KvitteringFraOppdrag, feilmelding: Feilmelding) {
            feilmelding.id shouldBe 1L
            feilmelding.kreditorTrekkId shouldBe kvittering.dokument.innrapporteringTrekk.kreditorTrekkId
            feilmelding.trekkAlternativ shouldBe kvittering.dokument.innrapporteringTrekk.kodeTrekkAlternativ
            feilmelding.transaksjonsId shouldBe kvittering.dokument.transaksjonsId
            feilmelding.feilkode shouldBe kvittering.mmel!!.kodeMelding
            feilmelding.beskrivelse shouldBe kvittering.mmel.beskrMelding
        }
        Given("Data fra skatt skal lagres") {
            val trekkpaalegg = json.decodeFromString<List<Trekkpaalegg>>(resourceToString("Trekk_med_to_trekkalternativ.json")).first()

            When("Trekkpålegg lagres") {
                val id = saveTrekkpaalegg(trekkpaalegg)
                id.shouldNotBeNull()

                Then("Skal Trekkpålegg lagres i tabellen 'fraskatt'") {
                    val lagretTrekk = getUtleggsTrekk(id)
                    lagretTrekk.shouldNotBeNull()
                    compareTrekk(trekkpaalegg, lagretTrekk)
                }
                And("TrekkstørrelseForPeriode skal lagres i tabellen 'peride'") {
                    val lagretPeriode = getPerioderForTrekk(id)
                    comparePerioder(trekkpaalegg.trekkstoerrelseForPeriode, lagretPeriode)
                }
                And("Betalingsinformasjon skal lagres i tabellen 'betalingsinformasjonfraskatt'") {
                    val lagretBetalingsinformasjon = getBetalingsinformasjonForTrekk(id)
                    lagretBetalingsinformasjon.shouldNotBeNull()
                    compareBetalingsinformasjon(trekkpaalegg.betalingsinformasjon, lagretBetalingsinformasjon)
                }
            }
        }
        Given("En kvittering har feil") {

            val kvittering = json.decodeFromString<KvitteringFraOppdrag>(resourceToString("kvittering-feil.json"))
            When("Feil lagres") {
                DBListener.dataSource.withTransaction { session -> repositoryNy.insertFeilmeldingFraOS(kvittering, session) }

                Then("Skal trekkid, trekkalternativ, corrid, feilkode og beskrivelse lagres") {
                    val feilmelding =
                        DBListener.dataSource.withTransaction { session ->
                            repositoryNy.getFeilmeldingerFraOS(
                                kvittering.dokument.transaksjonsId,
                                session,
                            )
                        }
                    feilmelding.shouldNotBeNull()
                    compareFeilmelding(kvittering, feilmelding)
                }
            }
        }

    /*    xcontext("disabled") {

            fun comparePerioder(trekkpaalegg: Trekkpaalegg, perioder: List<TrekkPeriodeTable>) {
                val dbPerioder = perioder.sortedBy { it.datoStart }
                val trekkPerioder = trekkpaalegg.trekkstoerrelseForPeriode.sortedBy(TrekkstorrelseForPeriode::startdato)

                trekkpaalegg.trekkstoerrelseForPeriode.size shouldBe dbPerioder.size
                dbPerioder.forEachIndexed { index, dbPeriode ->
                    val periode = trekkPerioder[index]
                    dbPeriode.datoStart shouldBe periode.startdato
                    dbPeriode.datoSlutt shouldBe (
                        periode.sluttdato
                            ?: "9999-12-31"
                    ) // TODO: We should probably just keep the nulls.
                    dbPeriode.trekkversjon shouldBe trekkpaalegg.trekkversjon
                    dbPeriode.sekvensnummer shouldBe trekkpaalegg.sekvensnummer
                    dbPeriode.trekkidSke shouldBe trekkpaalegg.trekkid
                    if (dbPeriode.trekkAlternativ == LOPM) {
                        periode.trekkprosent shouldBe null
                        dbPeriode.sats shouldBe periode.trekkbeloep!!.trekkbeloep
                    }
                    if (dbPeriode.trekkAlternativ == LOPP) {
                        periode.trekkbeloep shouldBe null
                        dbPeriode.sats shouldBe periode.trekkprosent!!.trekkprosent
                    }
                }
            }

            fun compareTrekk(trekkpaalegg: Trekkpaalegg, table: UtleggstrekkTable) {
                table.trekkstatus shouldBe trekkpaalegg.trekkstatus
                table.trekkidSke shouldBe trekkpaalegg.trekkid
                table.saksnummer shouldBe trekkpaalegg.saksnummer
                table.skyldner shouldBe trekkpaalegg.skyldner
                table.opprettetSke.toInstant(TimeZone.currentSystemDefault()) shouldBe trekkpaalegg.opprettet
                table.trekkversjon shouldBe trekkpaalegg.trekkversjon
                table.saksnummer shouldBe trekkpaalegg.saksnummer
                table.skyldner shouldBe trekkpaalegg.skyldner
                table.trekkpliktig shouldBe trekkpaalegg.trekkpliktig

                table.betalingsmottaker shouldBe trekkpaalegg.betalingsinformasjon.betalingsmottaker
                table.kid shouldBe trekkpaalegg.betalingsinformasjon.kidnummer
                table.kontonummer shouldBe trekkpaalegg.betalingsinformasjon.kontonummer

                val dbPerioder = dataSource.withTransaction { session -> repository.fetchAllPerioderForTrekk(table, session) }
                comparePerioder(trekkpaalegg, dbPerioder)
            }
            test("Hent sekvensnummer") {
                val sek = dataSource.withTransaction { session -> repository.fetchLastSekvensnr(session) }
                sek shouldBe 0
                val trekkpalegg =
                    json.decodeFromString<List<Trekkpaalegg>>(resourceToString("FraSkatt_Trekkversjon1_1Trekkalternativ-2trekk.json"))
                saveUtleggsrekk(trekkpalegg)
                dataSource.withTransaction { session -> repository.fetchLastSekvensnr(session) } shouldBe trekkpalegg.maxOf { it.sekvensnummer }
            }

            test("Eksisterer trekk") {
                dataSource.withTransaction { session -> repository.doesTrekkExist("1", 1, 1, session) } shouldBe false
                val trekkpalegg =
                    json.decodeFromString<List<Trekkpaalegg>>(resourceToString("FraSkatt_Trekkversjon1_1Trekkalternativ-2trekk.json"))
                saveUtleggsrekk(trekkpalegg)
                dataSource.withTransaction { session ->
                    repository.fetchLastSekvensnr(session) shouldBe trekkpalegg.maxOf { it.sekvensnummer }
                    repository.doesTrekkExist("1", 1, 1, session) shouldBe true
                    repository.doesTrekkExist("2_xx", 2, 1, session) shouldBe true
                    repository.doesTrekkExist("1", 2, 2, session) shouldBe false
                }
            }

            test("Oppdater navtrekkstatus") {
                val trekkpalegg =
                    json.decodeFromString<List<Trekkpaalegg>>(resourceToString("FraSkatt_Trekkversjon1_1Trekkalternativ-2trekk.json"))
                saveUtleggsrekk(trekkpalegg)

                val trekk =
                    dataSource
                        .withTransaction { session ->
                            repository.fetchTrekkNotSendt(session)
                        }.find { it.sekvensnummer == 1 }!!

                dataSource.withTransaction { session ->
                    repository.updateNavTrekkStatus(trekk.corrid, SENDT, session)
                }
                dataSource.withTransaction { session ->
                    val updatedTrekk = repository.findTrekkByCorrId(trekk.corrid, session)!!
                    updatedTrekk.status shouldBe SENDT
                    updatedTrekk.tidspunktSisteStatus shouldNotBe trekk.tidspunktSisteStatus
                }
            }

            test("Oppdater trekkstatus") {
                val trekkpalegg =
                    json.decodeFromString<List<Trekkpaalegg>>(resourceToString("FraSkatt_Trekkversjon1_1Trekkalternativ-2trekk.json"))
                saveUtleggsrekk(trekkpalegg)
                sleep(1)

                val trekk =
                    dataSource
                        .withTransaction { session -> repository.fetchTrekkNotSendt(session) }
                        .find { it.sekvensnummer == 1 }!!
                trekk.status shouldBe MOTTATT

                dataSource.withTransaction { session ->
                    repository.updateTrekkStatusSentAndDateTimeSentOS(trekk.corrid, session)
                }
                dataSource.withTransaction { session ->
                    repository.fetchTrekkNotSendt(session).find { it.sekvensnummer == 1 } shouldBe null
                    val updatedTrekk = repository.findTrekkByCorrId(trekk.corrid, session)!!
                    updatedTrekk.status shouldBe SENDT
                    updatedTrekk.tidspunktSisteStatus shouldNotBe trekk.tidspunktSisteStatus
                }
            }

            test("Oppdater kvitteringsstatus") {
                val trekkpalegg =
                    json.decodeFromString<List<Trekkpaalegg>>(resourceToString("FraSkatt_Trekkversjon1_1Trekkalternativ-2trekk.json"))
                saveUtleggsrekk(trekkpalegg)

                val trekk = dataSource.withTransaction { session -> repository.fetchTrekkNotSendt(session) }.find { it.sekvensnummer == 1 }!!
                dataSource.withTransaction { session ->
                    repository.updateKvitteringStatus(
                        trekk.corrid,
                        KVITTERING_OK,
                        "kvitteringLOPM", // This is the column name in the Utleggstrekk table.
                        "navID",
                        LOPM,
                        session,
                    )
                }

                val updatedTrekk =
                    dataSource.withTransaction { session ->
                        repository.findTrekkByCorrId(trekk.corrid, session)!!
                    }
                trekk.kvitteringLOPM shouldNotBe updatedTrekk.kvitteringLOPM
                trekk.tidspunktSisteStatus shouldNotBe updatedTrekk.tidspunktSisteStatus
                updatedTrekk.status shouldBe KVITTERING_OK
                updatedTrekk.trekkidNavLOPM shouldBe "navID"
            }

            test("Hent alle perioder for trekkversjon") {
                val trekkpalegg =
                    json.decodeFromString<List<Trekkpaalegg>>(resourceToString("FraSkatt_Trekkversjon1_1Trekkalternativ-2trekk.json"))
                saveUtleggsrekk(trekkpalegg)

                val trekkFraSkatt = trekkpalegg.find { it.sekvensnummer == 1 }!!

                val trekk = dataSource.withTransaction { session -> repository.fetchTrekkNotSendt(session) }.find { it.sekvensnummer == 1 }!!
                val perioder = dataSource.withTransaction { session -> repository.fetchAllPerioderForTrekk(trekk, session) }
                comparePerioder(trekkFraSkatt, perioder)
            }

            test("Sjekk rekkefølge på utleggstrekk") {
                val trekkpalegg = json.decodeFromString<List<Trekkpaalegg>>(resourceToString("diverse_trekk_ut_av_sekvens.json"))
                saveUtleggsrekk(trekkpalegg)
                val trekkNotSent =
                    dataSource.withTransaction { session ->
                        repository.fetchTrekkNotSendt(session)
                    }
                // Alle trekk skal være i rekkefølge
                trekkNotSent.map(UtleggstrekkTable::sekvensnummer).zipWithNext().all { (a, b) -> a < b } shouldBe true
            }
        }*/
    })

private fun compareTrekk(trekkpaalegg: Trekkpaalegg, lagret: TrekkFraSkatt) {
    lagret.trekkstatus shouldBe trekkpaalegg.trekkstatus.name
    lagret.trekkid shouldBe trekkpaalegg.trekkid
    lagret.saksnummer shouldBe trekkpaalegg.saksnummer
    lagret.skyldner shouldBe trekkpaalegg.skyldner
    //   lagret.opprettetSke.toInstant(TimeZone.currentSystemDefault()) shouldBe trekkpaalegg.opprettet
    lagret.trekkversjon shouldBe trekkpaalegg.trekkversjon
    lagret.saksnummer shouldBe trekkpaalegg.saksnummer
    lagret.skyldner shouldBe trekkpaalegg.skyldner
    lagret.trekkpliktig shouldBe trekkpaalegg.trekkpliktig

    // val dbPerioder = dataSource.withTransaction { session -> repository.fetchAllPerioderForTrekk(table, session) }
    // comparePerioder(trekkpaalegg, dbPerioder)
}
