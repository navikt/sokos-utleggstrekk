package no.nav.sokos.utleggstrekk.database

import java.lang.Thread.sleep

import kotlin.time.ExperimentalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.json.Json

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

import no.nav.sokos.utleggstrekk.database.TestRepositoryExtensions.clearDb
import no.nav.sokos.utleggstrekk.database.model.TrekkPeriodeTable
import no.nav.sokos.utleggstrekk.database.model.UtleggstrekkTable
import no.nav.sokos.utleggstrekk.domene.nav.TrekkAlternativ
import no.nav.sokos.utleggstrekk.domene.nav.TrekkTilOppdrag
import no.nav.sokos.utleggstrekk.domene.ske.Trekkpaalegg
import no.nav.sokos.utleggstrekk.domene.ske.TrekkstorrelseForPeriode
import no.nav.sokos.utleggstrekk.util.TestContainer
import no.nav.sokos.utleggstrekk.util.resourceToString
import no.nav.sokos.utleggstrekk.utils.SQLUtils.withTransaction

@OptIn(ExperimentalTime::class)
class RepositoryTest :
    FunSpec({
        val testContainer = TestContainer()
        val dataSource = testContainer.dataSource
        val repository = Repository(testContainer.dataSource)

        beforeTest {
            dataSource.withTransaction { session ->
                repository.clearDb(session)
            }
        }

        fun saveUtleggsrekk(trekkpalegg: List<Trekkpaalegg>) {
            dataSource.withTransaction { session ->
                repository.saveAllNewUtleggstrekk(trekkpalegg, session)
            }
        }

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
                if (dbPeriode.trekkAlternativ == "LOPM") {
                    periode.trekkprosent shouldBe null
                    dbPeriode.sats shouldBe periode.trekkbeloep!!.trekkbeloep
                }
                if (dbPeriode.trekkAlternativ == "LOPP") {
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

        val json =
            Json {
                prettyPrint = true
                isLenient = true
                explicitNulls = false
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
                repository.updateNavTrekkStatus(trekk.corrid, "SOMESTATE", session)
            }
            dataSource.withTransaction { session ->
                val updatedTrekk = repository.findTrekkByCorrId(trekk.corrid, session)!!
                updatedTrekk.status shouldBe "SOMESTATE"
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
            trekk.status shouldBe "MOTTATT"

            dataSource.withTransaction { session ->
                repository.updateTrekkStatusSentAndDateTimeSentOS(trekk.corrid, session)
            }
            dataSource.withTransaction { session ->
                repository.fetchTrekkNotSendt(session).find { it.sekvensnummer == 1 } shouldBe null
                val updatedTrekk = repository.findTrekkByCorrId(trekk.corrid, session)!!
                updatedTrekk.status shouldBe "SENDT"
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
                    "KVITTERING_OK",
                    "kvitteringLOPM",
                    "navID",
                    TrekkAlternativ.LOPM.value,
                    session,
                )
            }

            val updatedTrekk =
                dataSource.withTransaction { session ->
                    repository.findTrekkByCorrId(trekk.corrid, session)!!
                }
            trekk.kvitteringLOPM shouldNotBe updatedTrekk.kvitteringLOPM
            trekk.tidspunktSisteStatus shouldNotBe updatedTrekk.tidspunktSisteStatus
            updatedTrekk.status shouldBe "KVITTERING_OK"
            updatedTrekk.trekkidNav shouldBe "navID"
        }

        test("Lagre trekk") {
            val trekkpalegg = json.decodeFromString<List<Trekkpaalegg>>(resourceToString("Trekk_med_to_trekkalternativ.json"))
            saveUtleggsrekk(trekkpalegg)

            val dbTrekk = dataSource.withTransaction { session -> repository.fetchTrekkNotSendt(session) }
            trekkpalegg.size shouldBe dbTrekk.size
            for (trekk in trekkpalegg) {
                compareTrekk(trekk, dbTrekk.find { it.sekvensnummer == trekk.sekvensnummer }!!)
            }
        }

        test("Lagre feilkoder") {
            val kvittering = json.decodeFromString<TrekkTilOppdrag>(resourceToString("kvittering-feil.json"))
            dataSource.withTransaction { session -> repository.saveFeilkoder(kvittering, session) }

            val feilkode =
                dataSource.withTransaction { session ->
                    repository.findFeilkode(
                        kvittering.dokument.transaksjonsId,
                        session,
                    )!!
                }
            feilkode.feilkodeTableId shouldBe 1L
            feilkode.trekkIdNav shouldBe kvittering.dokument.innrapporteringTrekk.kreditorTrekkId
            feilkode.trekkAlternativ shouldBe kvittering.dokument.innrapporteringTrekk.kodeTrekkAlternativ
            feilkode.corrId shouldBe kvittering.dokument.transaksjonsId
            feilkode.feilkode shouldBe kvittering.mmel!!.kodeMelding
            feilkode.beskrivelse shouldBe kvittering.mmel.beskrMelding
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
    })
