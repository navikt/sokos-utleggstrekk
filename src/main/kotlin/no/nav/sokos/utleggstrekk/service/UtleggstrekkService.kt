package no.nav.sokos.utleggstrekk.service

import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.serialization.JsonConvertException
import mu.KotlinLogging
import no.nav.sokos.utleggstrekk.client.SkeClient
import no.nav.sokos.utleggstrekk.domene.ske.Utleggstrekk
import no.nav.sokos.utleggstrekk.mq.MqProducer
import java.util.concurrent.atomic.AtomicInteger

private val logger = KotlinLogging.logger { }

class UtleggstrekkService(
    private val databaseService: DatabaseService,
    private val genererTrekkService: GenererTrekkService,
    private val skeClient: SkeClient = SkeClient(),
    private val mqProducer: MqProducer = MqProducer(),
) {
    suspend fun behandleUtleggstrekk(): Pair<AtomicInteger, AtomicInteger> {
        lagreNyeUtleggstrekk()
        return sendTrekkTilOS()
    }

    suspend fun lagreNyeUtleggstrekk(): List<Utleggstrekk> =
        skeClient
            .hentAlleUtleggstrekk()
            .toUtleggsTrekk()
            .filter { !databaseService.trekkFinnes(it.sekvensnummer) }

    private fun sendTrekkTilOS(): Pair<AtomicInteger, AtomicInteger> {
        val resultat = mqProducer.send(lagXmlAvNyeTrekk())
        mqProducer.commit()
        return resultat
    }

    private fun lagXmlAvNyeTrekk(): List<String> =
        databaseService.hentAlleTrekkSomIkkeErSendt().run {
            genererTrekkService.lagTrekkTilOs(this).map {
                NyXmlService.xmlOf(it)
            }
        }

    private suspend fun HttpResponse.toUtleggsTrekk() =
        try {
            body<List<Utleggstrekk>>()
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

    // Brukes kun av testAPI
    suspend fun hentUtleggstrekkFraSekvensnrOgLagreAlleNye(sekvensnr: Int): List<Utleggstrekk> {
        println("henter allefra sekvensnr sekvensnr: $sekvensnr")
        val trekkListe = skeClient.hentUtleggstrekkFraSekvensnr(sekvensnr).toUtleggsTrekk()
        return trekkListe.mapNotNull { it.takeIf { !databaseService.trekkFinnes(it.sekvensnummer) } }
    }
}