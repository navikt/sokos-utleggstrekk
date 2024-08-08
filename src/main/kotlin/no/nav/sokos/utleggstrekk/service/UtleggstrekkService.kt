package no.nav.sokos.utleggstrekk.service

import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.serialization.JsonConvertException
import mu.KotlinLogging
import no.nav.sokos.utleggstrekk.client.SkeClient
import no.nav.sokos.utleggstrekk.database.model.TrekkTable
import no.nav.sokos.utleggstrekk.domene.ske.Utleggstrekk
import no.nav.sokos.utleggstrekk.mq.MqProducer

private val logger = KotlinLogging.logger { }

class UtleggstrekkService(
    private val databaseService: DatabaseService,
    private val genererTrekkService: GenererTrekkService,
    private val skeClient: SkeClient = SkeClient(),
    private val mqProducer: MqProducer = MqProducer(),
) {
    suspend fun behandleUtleggstrekk(): List<Boolean> {
        lagreNyeUtleggstrekk()
        return sendTrekkTilOS()
    }

    suspend fun lagreNyeUtleggstrekk() {
        skeClient
            .hentAlleUtleggstrekk()
            .toUtleggsTrekk()
            .mapNotNull { it.takeIf { !databaseService.trekkFinnes(it.trekkid, it.sekvensnummer, it.trekkversjon) } }
            .let {
                databaseService.lagreUtleggstrekk(it)
            }
    }

    private fun sendTrekkTilOS(): List<Boolean> {
        val alleNyeTrekk = databaseService.hentAlleTrekkSomIkkeErSendt()
        val resultat = lagXmlAvTrekk(alleNyeTrekk).map { mqProducer.send(it) }
        mqProducer.commit()

        alleNyeTrekk.forEach {
            databaseService.oppdaterTrekkStatus(it)
        }
        return resultat
    }

    private fun lagXmlAvTrekk(trekk: List<TrekkTable>): List<String> =
        trekk.run {
            genererTrekkService.lagTrekkTilOs(this).map {
                NyXmlService.xmlOf(it)
            }
        }

    private suspend fun HttpResponse.toUtleggsTrekk() =
        try {
            body<List<Utleggstrekk>>().also { println(it) }
        } catch (e: JsonConvertException) {
            logger.error { "Feil i konvertering av response: ${e.message}" }
            emptyList()
        }

    // Brukes kun av testAPI
    suspend fun hentAlleNyeUtleggstrekk(): List<Utleggstrekk> {
        println("hent alle NYE!")
        val sisteSekvensnr = databaseService.hentSisteSekvensnummer()
        println("Henter fra siste sekvensnr: $sisteSekvensnr")
        return hentUtleggstrekkFraSekvensnrOgLagreAlleNye(sisteSekvensnr)
    }

    suspend fun hentAlleNye() = skeClient.hentAlleUtleggstrekk().body<List<Utleggstrekk>>()

    // Brukes kun av testAPI
    suspend fun hentUtleggstrekkFraSekvensnrOgLagreAlleNye(sekvensnr: Int): List<Utleggstrekk> {
        println("henter allefra sekvensnr sekvensnr: $sekvensnr")
        val trekkListe = skeClient.hentUtleggstrekkFraSekvensnr(sekvensnr).toUtleggsTrekk()
        return trekkListe.mapNotNull { it.takeIf { !databaseService.trekkFinnes(it.trekkid, it.sekvensnummer, it.trekkversjon) } }
    }
}