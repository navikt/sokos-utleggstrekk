package no.nav.sokos.utleggstrekk.service

import com.zaxxer.hikari.HikariDataSource
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotliquery.queryOf

import no.nav.sokos.utleggstrekk.client.SkeClient
import no.nav.sokos.utleggstrekk.database.model.UtleggstrekkStatus
import no.nav.sokos.utleggstrekk.database.model.UtleggstrekkTable
import no.nav.sokos.utleggstrekk.mq.JmsProducerService
import no.nav.sokos.utleggstrekk.util.TestContainer
import no.nav.sokos.utleggstrekk.util.TestData.Trekkpaalegg

internal class UtleggsTrekkServiceTest :
    BehaviorSpec({
        val testContainer = TestContainer()
        val databaseService = DatabaseService(testContainer.dataSource)
        val capturedPayloads = mutableListOf<String>()

        val mqProducerMock =
            mockk<JmsProducerService>(relaxed = true) {
                every { send(capture(capturedPayloads)) } just Runs
            }

        Given("Vi henter ett trekk fra SKE") {
            val trekkFraSkatt =
                Trekkpaalegg(
                    trekkId = "ID1",
                    sekvensnummer = 1,
                    trekkversjon = 1,
                )
            val skeClientMock =
                mockk<SkeClient> {
                    coEvery { hentUtleggstrekkFraSekvensnr(any()) } returns listOf(trekkFraSkatt)
                }

            val utleggsTrekkService =
                UtleggsTrekkService(
                    databaseService = databaseService,
                    skeClient = skeClientMock,
                    mqProducer = mqProducerMock,
                )

            testContainer.dataSource.getAllTrekk().size shouldBe 0

            When("Trekk sendes") {
                utleggsTrekkService.run()

                // TODO: Skal håndtering av serialisering flyttes til JMSProducer?
                Then("Skal trekk serialiseres riktig") {
                    capturedPayloads.forEach {
                        it shouldContain "SOKOSUTLEGG"
                        it shouldContain "TRK1"
                    }
                }

                Then("Skal status oppdateres til SENDT") {
                    val allTrekk = testContainer.dataSource.getAllTrekk()
                    allTrekk.size shouldBe 1
                    allTrekk
                        .filter { it.status == UtleggstrekkStatus.SENDT }
                        .size shouldBe 1
                }
            }
        }
    })

private fun HikariDataSource.getAllTrekk(): List<UtleggstrekkTable> =
    withTransaction { session ->
        session.list(
            queryOf(
                "SELECT * FROM utleggstrekk",
            ),
        ) { row -> UtleggstrekkTable(row) }
    }