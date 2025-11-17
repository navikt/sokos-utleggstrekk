package no.nav.sokos.utleggstrekk.service

import kotlinx.serialization.json.Json

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith

import no.nav.sokos.utleggstrekk.config.jsonConfig
import no.nav.sokos.utleggstrekk.database.model.SkattTrekkStatus
import no.nav.sokos.utleggstrekk.database.model.TransaksjonOS
import no.nav.sokos.utleggstrekk.domene.nav.DokumentTilOppdrag
import no.nav.sokos.utleggstrekk.domene.nav.TrekkAlternativ
import no.nav.sokos.utleggstrekk.domene.ske.Trekkpaalegg
import no.nav.sokos.utleggstrekk.listener.DBListener
import no.nav.sokos.utleggstrekk.util.resourceToString

internal class LifecycleTest :
    BehaviorSpec({
        extensions(DBListener)
        val json =
            Json {
                prettyPrint = true
                isLenient = true
                explicitNulls = false
            }

        val repository = DBListener.RepositoryNy

        Given("Vi har mottatt utleggstrekk...  ") {
            val bodyFraSkatt = resourceToString("FraSkatt_Trekkversjon1_1Trekkalternativ-2trekk.json")
            val paleggstrekkFraSkatt = json.decodeFromString<List<Trekkpaalegg>>(bodyFraSkatt)

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
                val behandleTrekkService = BehandleTrekkServiceNy(repository)
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
                val perioderFraSkatt = repository.getPerioderForTrekkVersjon(trekkFraSkatt!!.id)
                val betalingsInformasjon = repository.getBetalingsinformasjonForTrekk(trekkFraSkatt.id)
                val osdok: TransaksjonOS = trekkSomSkalSendes.first()
                val document = jsonConfig.decodeFromString<DokumentTilOppdrag>(osdok.documentJson)

                withClue("osDokumentet skal ha samme info som i trekk og perioder") {
                    with(document.innrapporteringTrekk) {
                        kreditorTrekkId shouldEndWith TrekkAlternativ.LOPM.suffix.toString()
                        kid shouldBe betalingsInformasjon!!.kidnummer
                        kodeTrekkAlternativ shouldBe perioderFraSkatt.first().trekkAlternativ()
                    }
                }
            }
        }
    })