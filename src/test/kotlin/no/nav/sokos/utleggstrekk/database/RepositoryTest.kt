package no.nav.sokos.utleggstrekk.database

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.json.Json
import no.nav.sokos.utleggstrekk.TestContainer
import no.nav.sokos.utleggstrekk.database.Repository.doesTrekkExist
import no.nav.sokos.utleggstrekk.database.Repository.fetchAllPerioderForTrekk
import no.nav.sokos.utleggstrekk.database.Repository.fetchLastSekvensnr
import no.nav.sokos.utleggstrekk.database.Repository.fetchTrekkNotSendt
import no.nav.sokos.utleggstrekk.database.Repository.saveAllNewUtleggstrekk
import no.nav.sokos.utleggstrekk.database.Repository.saveFeilkoder
import no.nav.sokos.utleggstrekk.database.Repository.updateKvitteringStatus
import no.nav.sokos.utleggstrekk.database.Repository.updateNavTrekkStatus
import no.nav.sokos.utleggstrekk.database.Repository.updateTrekkStatusSentAndDateTimeSentOS
import no.nav.sokos.utleggstrekk.database.model.TrekkPeriodeTable
import no.nav.sokos.utleggstrekk.database.model.UtleggstrekkTable
import no.nav.sokos.utleggstrekk.domene.nav.TrekkAlternativ
import no.nav.sokos.utleggstrekk.domene.nav.TrekkTilOppdrag
import no.nav.sokos.utleggstrekk.domene.ske.Trekkpaalegg
import no.nav.sokos.utleggstrekk.domene.ske.TrekkstorrelseForPeriode
import no.nav.sokos.utleggstrekk.util.resourceToString
import java.lang.Thread.sleep

class RepositoryTest :
    FunSpec({
        val testContainer = TestContainer()
        val connection = testContainer.dataSource.connection
        val repository = TrekkRepository(testContainer.dataSource)

        beforeTest {
            repository.clearDb()
        }

        fun comparePerioder(
            trekkpaalegg: Trekkpaalegg,
            perioder: List<TrekkPeriodeTable>,
        ) {
            val dbPerioder = perioder.sortedBy { it.datoStart }
            val trekkPerioder = trekkpaalegg.trekkstoerrelseForPeriode.sortedBy(TrekkstorrelseForPeriode::startdato)

            trekkpaalegg.trekkstoerrelseForPeriode.size shouldBe dbPerioder.size
            dbPerioder.forEachIndexed { index, dbPeriode ->
                val periode = trekkPerioder[index]
                dbPeriode.datoStart shouldBe periode.startdato
                dbPeriode.datoSlutt shouldBe (periode.sluttdato
                    ?: "9999-12-31")  // TODO: We should probably just keep the nulls.
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

        fun compareTrekk(
            trekkpaalegg: Trekkpaalegg,
            table: UtleggstrekkTable
        ) {
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

            val dbPerioder = connection.fetchAllPerioderForTrekk(table)
            comparePerioder(trekkpaalegg, dbPerioder)
        }

        val json = Json {
            prettyPrint = true
            isLenient = true
            explicitNulls = false
        }

        test("Hent sekvensnummer") {
            val sek = connection.fetchLastSekvensnr()
            sek shouldBe 0
            val trekkpalegg =
                json.decodeFromString<List<Trekkpaalegg>>(resourceToString("FraSkatt_Trekkversjon1_1Trekkalternativ-2trekk.json"))
            connection.saveAllNewUtleggstrekk(trekkpalegg)
            connection.fetchLastSekvensnr() shouldBe trekkpalegg.maxOf { it.sekvensnummer }
        }

        test("Eksisterer trekk") {
            connection.doesTrekkExist("1", 1, 1) shouldBe false
            val trekkpalegg =
                json.decodeFromString<List<Trekkpaalegg>>(resourceToString("FraSkatt_Trekkversjon1_1Trekkalternativ-2trekk.json"))
            connection.saveAllNewUtleggstrekk(trekkpalegg)
            connection.fetchLastSekvensnr() shouldBe trekkpalegg.maxOf { it.sekvensnummer }
            connection.doesTrekkExist("1", 1, 1) shouldBe true
            connection.doesTrekkExist("2_xx", 2, 1) shouldBe true
            connection.doesTrekkExist("1", 2, 2) shouldBe false
        }

        test("Oppdater navtrekkstatus") {
            val trekkpalegg =
                json.decodeFromString<List<Trekkpaalegg>>(resourceToString("FraSkatt_Trekkversjon1_1Trekkalternativ-2trekk.json"))
            connection.saveAllNewUtleggstrekk(trekkpalegg)
            val trekk = connection.fetchTrekkNotSendt().find { it.sekvensnummer == 1 }!!
            connection.updateNavTrekkStatus(trekk.corrid, "SOMESTATE")

            val updatedTrekk = repository.findTrekkByCorrId(trekk.corrid)!!
            updatedTrekk.status shouldBe "SOMESTATE"
            updatedTrekk.tidspunktSisteStatus shouldNotBe trekk.tidspunktSisteStatus
        }

        test("Oppdater trekkstatus") {
            val trekkpalegg =
                json.decodeFromString<List<Trekkpaalegg>>(resourceToString("FraSkatt_Trekkversjon1_1Trekkalternativ-2trekk.json"))
            connection.saveAllNewUtleggstrekk(trekkpalegg)
            sleep(1)
            val trekk = connection.fetchTrekkNotSendt().find { it.sekvensnummer == 1 }!!
            trekk.status shouldBe "MOTTATT"
            connection.updateTrekkStatusSentAndDateTimeSentOS(trekk.corrid)
            connection.fetchTrekkNotSendt().find { it.sekvensnummer == 1 } shouldBe null

            val updatedTrekk = repository.findTrekkByCorrId(trekk.corrid)!!
            updatedTrekk.status shouldBe "SENDT"
            updatedTrekk.tidspunktSisteStatus shouldNotBe trekk.tidspunktSisteStatus
        }

        test("Oppdater kvitteringsstatus") {
            val trekkpalegg =
                json.decodeFromString<List<Trekkpaalegg>>(resourceToString("FraSkatt_Trekkversjon1_1Trekkalternativ-2trekk.json"))
            connection.saveAllNewUtleggstrekk(trekkpalegg)
            val trekk = connection.fetchTrekkNotSendt().find { it.sekvensnummer == 1 }!!
            connection.updateKvitteringStatus(
                trekk.corrid,
                "KVITTERING_OK",
                "kvitteringLOPM",
                "navID",
                TrekkAlternativ.LOPM.value
            )
            val updatedTrekk = repository.findTrekkByCorrId(trekk.corrid)!!

            trekk.kvitteringLOPM shouldNotBe updatedTrekk.kvitteringLOPM
            trekk.tidspunktSisteStatus shouldNotBe updatedTrekk.tidspunktSisteStatus
            updatedTrekk.status shouldBe "KVITTERING_OK"
            updatedTrekk.trekkidNav shouldBe "navID"
        }

        test("Lagre trekk") {
            val trekkpalegg = json.decodeFromString<List<Trekkpaalegg>>(resourceToString("Trekk_med_to_trekkalternativ.json"))
            connection.saveAllNewUtleggstrekk(trekkpalegg)
            val dbTrekk = connection.fetchTrekkNotSendt()
            trekkpalegg.size shouldBe dbTrekk.size
            for ( trekk in trekkpalegg ) {
                compareTrekk(trekk, dbTrekk.find { it.sekvensnummer == trekk.sekvensnummer }!! )
            }
        }

        test("Lagre feilkoder") {
            val kvittering = json.decodeFromString<TrekkTilOppdrag>(resourceToString("kvittering-feil.json"))
            connection.saveFeilkoder(listOf(kvittering))
            val feilkode = repository.findFeilkode( kvittering.dokument.transaksjonsId )!!

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
            connection.saveAllNewUtleggstrekk(trekkpalegg)
            val trekkFraSkatt = trekkpalegg.find { it.sekvensnummer == 1 }!!
            val trekk = connection.fetchTrekkNotSendt().find { it.sekvensnummer == 1 }!!
            val perioder = connection.fetchAllPerioderForTrekk(trekk)
            comparePerioder(trekkFraSkatt, perioder)
        }
    })