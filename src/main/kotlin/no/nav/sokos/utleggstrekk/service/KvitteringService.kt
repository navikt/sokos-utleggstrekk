package no.nav.sokos.utleggstrekk.service

import kotlinx.serialization.json.Json
import no.nav.sokos.utleggstrekk.domene.nav.TrekkTilOppdrag
import no.nav.sokos.utleggstrekk.mq.MqConsumer

class KvitteringService(
    private val databaseService: DatabaseService,
    private val mqConsumer: MqConsumer = MqConsumer(),
) {


    fun behandleKvitteringer(){
        val kvitteringer = hentAlleKvitteringer()
        lagreKvitteringer(kvitteringer)
        varsleFeil(kvitteringer)
    }

    fun hentAlleKvitteringer():List<TrekkTilOppdrag> =
        hentAlleKvitteringerFraMq().map {
            Json.decodeFromString<TrekkTilOppdrag>(it).also { println(it) }
        }
    private fun hentAlleKvitteringerFraMq():List<String>{
        val kvitteringer = mutableListOf<String>()
        do {
            val  svar = mqConsumer.receive()
            if (svar != null) {
                kvitteringer.add(svar)
                println("fra MQ: $svar")
            }else{
                println("Ingen melding fra MQ")
            }
        }while (svar != null)
        return kvitteringer
    }
    private fun lagreKvitteringer(kvitteringer: List<TrekkTilOppdrag>) {
        TODO("Not yet implemented")
    }
    private fun varsleFeil(kvitteringer: List<TrekkTilOppdrag>) {
        TODO("Not yet implemented")
    }

}