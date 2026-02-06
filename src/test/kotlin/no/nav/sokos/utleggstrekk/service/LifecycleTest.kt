package no.nav.sokos.utleggstrekk.service

import java.time.Instant
import java.time.temporal.ChronoUnit.DAYS

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldEndWith
import kotliquery.queryOf

import no.nav.sokos.utleggstrekk.config.jsonConfig
import no.nav.sokos.utleggstrekk.database.Repository
import no.nav.sokos.utleggstrekk.database.TestRepository.getTrekkFraSkatt
import no.nav.sokos.utleggstrekk.database.model.KvitteringStatus
import no.nav.sokos.utleggstrekk.database.model.SkattTrekkStatus
import no.nav.sokos.utleggstrekk.database.model.TransaksjonOS
import no.nav.sokos.utleggstrekk.domene.nav.KvitteringFraOppdrag
import no.nav.sokos.utleggstrekk.domene.nav.Mmel
import no.nav.sokos.utleggstrekk.domene.nav.TrekkAlternativ
import no.nav.sokos.utleggstrekk.domene.nav.TrekkTilOppdrag
import no.nav.sokos.utleggstrekk.domene.ske.Trekkpaalegg
import no.nav.sokos.utleggstrekk.listener.DBListener
import no.nav.sokos.utleggstrekk.util.resourceToString

internal class LifecycleTest :
    BehaviorSpec({
        extensions(DBListener)
        val repository by lazy {
            DBListener.repository
        }

        Given("Vi har mottatt utleggstrekk...  ") {
            val bodyFraSkatt = resourceToString("FraSkatt_Trekkversjon1_1Trekkalternativ-2trekk.json")
            val paleggstrekkFraSkatt = jsonConfig.decodeFromString<List<Trekkpaalegg>>(bodyFraSkatt)

            then("lagres disse i database") {
                paleggstrekkFraSkatt.forEach { repository.insertTrekkFraSkatt(it) }
                val trekk = repository.getAllTrekkFraSkatt()
                trekk shouldHaveSize 2
                val perioder = trekk.map { repository.getPerioderForTrekk(it) }.flatten()
                perioder shouldHaveSize 4
            }
            then("sjekker at dataene er lagret riktig") {
                val dbdataTrekk = repository.getAllTrekkFraSkatt()
                val dbdataPerioder = repository.getPerioderForTrekkVersjon(dbdataTrekk.first().id)
                dbdataTrekk.first().trekkid shouldBe paleggstrekkFraSkatt.first().trekkid
                dbdataTrekk.first().trekkversjon shouldBe paleggstrekkFraSkatt.first().trekkversjon
                dbdataTrekk.first().skyldner shouldBe paleggstrekkFraSkatt.first().skyldner
                dbdataTrekk.first().sekvensnummer shouldBe paleggstrekkFraSkatt.first().sekvensnummer
                dbdataPerioder.mapIndexed { i, periode ->
                    if (periode.trekkAlternativ() == TrekkAlternativ.LOPP) {
                        periode.satsFor(TrekkAlternativ.LOPP) shouldBe
                            paleggstrekkFraSkatt
                                .first()
                                .trekkstoerrelseForPeriode
                                .get(i)
                                .trekkprosent
                                ?.trekkprosent
                    }
                    if (periode.trekkAlternativ() == TrekkAlternativ.LOPM) {
                        periode.satsFor(TrekkAlternativ.LOPM) shouldBe
                            paleggstrekkFraSkatt
                                .first()
                                .trekkstoerrelseForPeriode
                                .get(i)
                                .trekkbeloep
                                ?.trekkbeloep
                    }
                    periode.startdato shouldBe
                        paleggstrekkFraSkatt
                            .first()
                            .trekkstoerrelseForPeriode
                            .get(i)
                            .startdato
                    periode.sluttdato shouldBeIn
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
            then("Henter data fra database og sjekker trekk") {
                val behandleTrekkService = BehandleTrekkService(repository)
                behandleTrekkService.behandleTrekk()
                val trekkSomSkalSendes = repository.getTransaksjonerTilOsSomIkkeErSendt()

                trekkSomSkalSendes shouldHaveSize 3

                val (lopmTrekk, loppTrekk) = trekkSomSkalSendes.partition { it.trekkAlternativ == TrekkAlternativ.LOPM }

                lopmTrekk shouldHaveSize 2
                loppTrekk shouldHaveSize 1
            }
            then("Kontroller dokumenter som skal sendes til OS") {
                val dbdataTrekk = repository.getTrekkFraSkattMedStatus(SkattTrekkStatus.BEHANDLET)
                val trekkSomSkalSendes = repository.getTransaksjonerTilOsSomIkkeErSendt()

                withClue("Det skal være 2 trekk i db") {
                    dbdataTrekk.size shouldBe 2
                }
                val trekk1meldinger = trekkSomSkalSendes.filter { it.trekkIdSke == "1" }
                withClue("Det skal være 6 perioder for trekk 1") {
                    trekk1meldinger.map { it.perioder }.flatten() shouldHaveSize 6
                }
                withClue("Det skal være 2 trekkalternativer for trekk 1") {
                    trekkSomSkalSendes
                        .filter { it.trekkIdSke == "1" }
                        .map { it.trekkAlternativ }
                        .toSet() shouldHaveSize 2
                }
                withClue("Det skal være 3 perioder med LOPM, 1 med 0.0") {
                    val lopmPerioder =
                        trekk1meldinger
                            .filter { it.trekkAlternativ == TrekkAlternativ.LOPM }
                            .map { it.perioder }
                            .flatten()
                    lopmPerioder shouldHaveSize 3
                    lopmPerioder.filter { it.sats == 0.0 } shouldHaveSize 1
                }

                withClue("Det skal være 3 periode med LOPP, 2 med 0.0") {
                    val loppPerioder =
                        trekk1meldinger
                            .filter { it.trekkAlternativ == TrekkAlternativ.LOPP }
                            .map { it.perioder }
                            .flatten()
                    loppPerioder shouldHaveSize 3
                    loppPerioder.filter { it.sats == 0.0 } shouldHaveSize 2
                }
                val trekkFraSkatt = repository.getTrekkFraSkatt(1L)
                val perioderFraSkatt = repository.getPerioderForTrekkVersjon(trekkFraSkatt.id)
                val betalingsInformasjon = repository.getBetalingsinformasjonForTrekk(trekkFraSkatt.id)
                val osdok: TransaksjonOS = trekkSomSkalSendes.first()
                val trekkTilOppdrag = jsonConfig.decodeFromString<TrekkTilOppdrag>(osdok.documentJson)

                withClue("osDokumentet skal ha samme info som i trekk og perioder") {
                    with(trekkTilOppdrag.dokument.innrapporteringTrekk) {
                        kreditorTrekkId shouldEndWith TrekkAlternativ.LOPM.suffix.toString()
                        kid shouldBe betalingsInformasjon!!.kidnummer
                        kodeTrekkAlternativ shouldBe perioderFraSkatt.first().trekkAlternativ()
                    }
                }
            }
        }
        Given("Vi har tre utleggstrekk som har blitt sendt til og kvittert for av os") {
            DBListener.clearDB()
            val sevenMonthsAgo = Instant.now().minus(7 * 30, DAYS)
            val fiveMonthsAgo = Instant.now().minus(5 * 30, DAYS)
            val trekkpaalegg =
                jsonConfig.decodeFromString<Array<Trekkpaalegg>>(
                    resourceToString("lifecycleTestData/gamleTrekk.json")
                        .replace("###OPPRETTETGAMMEL###", sevenMonthsAgo.toString())
                        .replace("###OPPRETTETNY###", fiveMonthsAgo.toString()),
                )

            trekkpaalegg.forEach(repository::insertTrekkFraSkatt)
            repository.fakeTidspunktOpprettet("1", 1, sevenMonthsAgo)
            repository.fakeTidspunktOpprettet("1", 2, sevenMonthsAgo)
            repository.fakeTidspunktOpprettet("2", 1, sevenMonthsAgo)
            repository.fakeTidspunktOpprettet("3", 1, fiveMonthsAgo)
            val service = BehandleTrekkService(DBListener.repository)

            val trekkId = repository.getTrekkIdTilTrekkSomSkalBehandles()
            val idToTrekkId = trekkId.associateWith { repository.getTrekkFraSkatt(it).trekkid }
            service.behandleTrekk()
            val ikkeSendt = repository.getTransaksjonerTilOsSomIkkeErSendt()
            ikkeSendt.forEach { repository.updateTransaksjonSendt(it.transaksjonsID) }
            val skalSlettes1 = ikkeSendt.find { it.trekkIdSke == "1" && it.kreditorsref == "gammelv1" }!!
            repository.updateReceiptStatusOfTransaksjon(skalSlettes1.transaksjonsID, KvitteringStatus.OK, "navid1")
            val skalSlettes2 = ikkeSendt.find { it.trekkIdSke == "1" && it.kreditorsref == "gammelv2" }!!
            repository.updateReceiptStatusOfTransaksjon(skalSlettes2.transaksjonsID, KvitteringStatus.FEIL, "navid1")
            val kvittering =
                KvitteringFraOppdrag(
                    jsonConfig.decodeFromString<TrekkTilOppdrag>(skalSlettes2.documentJson).dokument,
                    mmel =
                        Mmel(
                            kodeMelding = "B720005F",
                            alvorlighetsgrad = "08",
                            beskrMelding = "Personen finnes ikke i PDL eller noe",
                        ),
                )
            repository.insertFeilmeldingFraOS(kvittering)
            val skalIkkeSlettes = ikkeSendt.filterNot { it.trekkIdSke == "1" }
            skalIkkeSlettes.forEach { tilOs ->
                repository.updateReceiptStatusOfTransaksjon(tilOs.transaksjonsID, KvitteringStatus.OK, "navid" + tilOs.trekkIdSke)
            }

            When("To trekk er eldre enn seks måneder, et av dem har trekkstatus avsluttet (1) og det tredje er yngre enn seks måneder") {
                Then("Skal alle versjoner av trekk 1 slettes under opprydding men ikke de to andre") {
                    repository.deleteOldData()

                    repository.getTrekkFraSkatt("1", 1) shouldBe null
                    repository.getTrekkFraSkatt("1", 2) shouldBe null
                    repository.getTransaksjonTilOs(skalSlettes1.transaksjonsID) shouldBe null
                    repository.getTransaksjonTilOs(skalSlettes2.transaksjonsID) shouldBe null
                    repository.getFeilmeldingerFraOS(skalSlettes2.transaksjonsID) shouldBe null

                    idToTrekkId.filterValues { it == "1" }.forEach { (key, _) ->
                        repository.getPerioderForTrekkVersjon(key) shouldBe emptyList()
                        repository.getBetalingsinformasjonForTrekk(key) shouldBe null
                    }

                    repository.getTrekkFraSkatt("2", 1) shouldNotBe null
                    repository.getTrekkFraSkatt("3", 1) shouldNotBe null

                    repository.getFeilmeldingerFraOS(skalSlettes2.transaksjonsID) shouldBe null
                }
            }
        }
    })

private fun Repository.fakeTidspunktOpprettet(trekkid: String, trekkversjon: Int, instant: Instant) {
    withTransaction { session ->
        session.update(
            queryOf(
                "UPDATE fraskatt SET tidspunkt_opprettet=:tidspunkt WHERE trekkid=:trekkid AND trekkversjon=:trekkversjon",
                mapOf("tidspunkt" to instant, "trekkid" to trekkid, "trekkversjon" to trekkversjon),
            ),
        ) shouldBe 1
    }
}