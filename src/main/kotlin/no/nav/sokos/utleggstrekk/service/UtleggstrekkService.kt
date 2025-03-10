package no.nav.sokos.utleggstrekk.service

import io.ktor.client.call.body
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import no.nav.sokos.utleggstrekk.client.SkeClient
import no.nav.sokos.utleggstrekk.domene.ske.Trekkpaalegg
import no.nav.sokos.utleggstrekk.mq.MqConsumer
import no.nav.sokos.utleggstrekk.mq.MqProducer
import no.nav.sokos.utleggstrekk.utils.oppdaterTrekkMedForskjelligSatstype
import no.nav.sokos.utleggstrekk.utils.toTrekkDokument
import no.nav.sokos.utleggstrekk.utils.toTrekkpaalegg


private val logger = KotlinLogging.logger { }

private const val SENDT = "SENDT"

class UtleggstrekkService(
    private val databaseService: DatabaseService,
    private val skeClient: SkeClient = SkeClient(),
    private val mqProducer: MqProducer = MqProducer(),
    private val mqConsumer: MqConsumer = MqConsumer(),
) {
    suspend fun behandleUtleggstrekk(): Int {
        hentOgLagreNyeUtleggstrekk()
        setTrekkAlternativPaNyeTrekk()
        return sendTrekkTilOS()
    }

    suspend fun hentOgLagreNyeUtleggstrekk() {
        val body = skeClient.hentAlleUtleggstrekk()
            body.toTrekkpaalegg().also { logger.info { "Hentet ${it.size} utleggstrekk fra Skatt" } }
            .mapNotNull { it.takeIf { !databaseService.trekkFinnes(it.trekkid, it.sekvensnummer, it.trekkversjon) } }
            .let {
                logger.info("Det er ${it.size} som skal lagres")
                databaseService.lagreUtleggstrekk(it)
            }
    }
    private fun sendTrekkTilOS(): Int {
        val trekkTilSending = databaseService.hentAlleTrekkSomIkkeErSendt()

        val trekkDokumentPairList = trekkTilSending.map { trekk ->
            val perioder = databaseService.hentAllePerioderForTrekkVersjon(trekk)
            trekk.toTrekkDokument(perioder) to trekk
        }

        runCatching {
            trekkDokumentPairList.map { trekkDokumentPair ->
                val dokument = Json.encodeToString(trekkDokumentPair.first)
                mqProducer.send(dokument) to trekkDokumentPair.second
            }
        }.onSuccess {
            it.forEach { forsokPair->
                if (forsokPair.first) databaseService.oppdaterTrekkStatus(forsokPair.second.corrid, SENDT)
            }
            return it.size // for test api
        }
        return 0 // for test api
    }

    suspend fun hentAlleNyeUtleggstrekk(): List<Trekkpaalegg> {
        val sisteSekvensnr = databaseService.hentSisteSekvensnummer()
        println("Henter fra siste sekvensnr: $sisteSekvensnr")
        return hentUtleggstrekkFraSekvensnrOgLagreAlleNye(sisteSekvensnr)
    }

    suspend fun hentAlleUtleggstrekk() = skeClient.hentAlleUtleggstrekk().body<List<Trekkpaalegg>>()

    suspend fun hentUtleggstrekkFraSekvensnrOgLagreAlleNye(sekvensnr: Int): List<Trekkpaalegg> {
        println("henter allefra sekvensnr sekvensnr: $sekvensnr")
        val trekkListe = skeClient.hentUtleggstrekkFraSekvensnr(sekvensnr).toTrekkpaalegg()
        return trekkListe.mapNotNull { it.takeIf { !databaseService.trekkFinnes(it.trekkid, it.sekvensnummer, it.trekkversjon) } }
    }

    suspend fun setTrekkAlternativPaNyeTrekk(){
        val trekkTilSjekk = databaseService.hentAlleTrekkutenTrekkAlternativ()
        trekkTilSjekk.map {
            val oppdatert = oppdaterTrekkMedForskjelligSatstype(it, databaseService.hentAllePerioderForTrekkVersjon(it))
            databaseService.oppdaterTrekkMedTrekkAlternativ(oppdatert)
        }
    }
}


