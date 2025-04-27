package no.nav.sokos.utleggstrekk.service

import kotlinx.serialization.json.Json
import no.nav.sokos.utleggstrekk.domene.nav.TrekkTilOppdrag
import no.nav.sokos.utleggstrekk.mq.MqConsumer

class KvitteringService(
    private val databaseService: DatabaseService,
    private val mqConsumer: MqConsumer = MqConsumer(),
) {

    fun hentAlleKvitteringer():List<TrekkTilOppdrag>{
        val mqKvitteringer = hentAlleKvitteringerFraMq()
        return mqKvitteringer.map {
            val kvittering = Json.decodeFromString<TrekkTilOppdrag>(it)
            println(kvittering)
            kvittering
        }

    }
    fun hentAlleKvitteringerFraMq():List<String>{
        val kvitteringer = mutableListOf<String>()
        do {
            val  svar = mqConsumer.receive()
            if (svar != null) {
                kvitteringer.add(svar)
                println("fra MQ: $svar")
            }else{
                println("Fikk NULL fra MQ")
            }
        }while (svar != null)
        return kvitteringer
    }
}