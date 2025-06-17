package no.nav.sokos.utleggstrekk.service

import kotlinx.serialization.json.Json
import mu.KotlinLogging
import no.nav.sokos.utleggstrekk.domene.nav.TrekkTilOppdrag
import no.nav.sokos.utleggstrekk.mq.MqConsumer

class KvitteringService(
    private val databaseService: DatabaseService,
    private val mqConsumer: MqConsumer = MqConsumer(),
) {

    private val logger = KotlinLogging.logger { }

    fun behandleKvitteringer() {
        val kvitteringer = hentAlleKvitteringer()
        lagreKvitteringerOgLoggFeil(kvitteringer)
    }

    fun hentAlleKvitteringer(): List<TrekkTilOppdrag> = hentAlleKvitteringerFraMq().map {
        Json.decodeFromString<TrekkTilOppdrag>(it)
    }

    private fun hentAlleKvitteringerFraMq(): List<String> {
        val kvitteringer = mutableListOf<String>()
        do {
            val svar = mqConsumer.receive()
            if (svar != null) {
                kvitteringer.add(svar)
            }
        } while (svar != null)
        return kvitteringer
    }

    private fun lagreKvitteringerOgLoggFeil(kvitteringer: List<TrekkTilOppdrag>) {
        databaseService.oppdaterTrekkMedKvitteringsinfo(kvitteringer)
        kvitteringer.filter { it.mmel!!.alvorlighetsgrad != "00" }.let {
            databaseService.lagreFeilkoderFraOS(it)
            varsleFeil(it)
        }
    }

    private fun varsleFeil(kvitteringerMedFeil: List<TrekkTilOppdrag>) {
        kvitteringerMedFeil.forEach {
            logger.info(
                "Trekk med kreditorstrekkID: ${it.dokument.innrapporteringTrekk.kreditorTrekkId}," +
                " corrid: ${it.dokument.transaksjonsId} har feilkode: ${it.mmel?.kodeMelding} og beskrivelse: ${it.mmel?.beskrMelding}"
            )
        }
        //TODO sjekke/vurdere om det skal sendes melding til slack og evt utføre det.
    }

}