package no.nav.sokos.utleggstrekk.service

import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.serialization.JsonConvertException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import no.nav.sokos.utleggstrekk.client.SkeClient
import no.nav.sokos.utleggstrekk.domene.ske.Trekkpaalegg
import no.nav.sokos.utleggstrekk.mq.MqProducer
import no.nav.sokos.utleggstrekk.utils.oppdaterTrekkMedForskjelligSatstype
import no.nav.sokos.utleggstrekk.utils.toTrekkDokument

private val logger = KotlinLogging.logger { }

private const val SENDT = "SENDT"
private const val TSSID = "80000423362"
private const val TSS_ORGNR = "971648198"
private const val TSS_KTO =  "76940512057"

class UtleggstrekkService(
    private val databaseService: DatabaseService,
    private val skeClient: SkeClient = SkeClient(),
    private val mqProducer: MqProducer = MqProducer(),
) {
    suspend fun behandleUtleggstrekk(): Int {
        lagreNyeUtleggstrekk()
        setTrekkAlternativPaNyeTrekk()
        return sendTrekkTilOS()
    }

    suspend fun lagreNyeUtleggstrekk() {
        val body = skeClient.hentAlleUtleggstrekk()
            body.toUtleggsTrekk()
            .mapNotNull { it.takeIf { !databaseService.trekkFinnes(it.trekkid, it.sekvensnummer, it.trekkversjon) } }
            .let {
                println("Det er ${it.size} som skal lagres")
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

    private suspend fun HttpResponse.toUtleggsTrekk() =
        try {
            body<List<Trekkpaalegg>>()
        } catch (e: JsonConvertException) {
            logger.error { "Feil i konvertering av response: ${e.message}" }
            emptyList()
        }

    suspend fun hentAlleNyeUtleggstrekk(): List<Trekkpaalegg> {
        val sisteSekvensnr = databaseService.hentSisteSekvensnummer()
        println("Henter fra siste sekvensnr: $sisteSekvensnr")
        return hentUtleggstrekkFraSekvensnrOgLagreAlleNye(sisteSekvensnr)
    }

    suspend fun hentAlleUtleggstrekk() = skeClient.hentAlleUtleggstrekk().body<List<Trekkpaalegg>>()

    suspend fun hentUtleggstrekkFraSekvensnrOgLagreAlleNye(sekvensnr: Int): List<Trekkpaalegg> {
        println("henter allefra sekvensnr sekvensnr: $sekvensnr")
        val trekkListe = skeClient.hentUtleggstrekkFraSekvensnr(sekvensnr).toUtleggsTrekk()
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


