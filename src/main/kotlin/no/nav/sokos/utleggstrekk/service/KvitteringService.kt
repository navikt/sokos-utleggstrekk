package no.nav.sokos.utleggstrekk.service

import kotlinx.serialization.json.Json
import mu.KotlinLogging
import no.nav.sokos.utleggstrekk.domene.nav.TrekkTilOppdrag
import no.nav.sokos.utleggstrekk.mq.MqConsumer

class KvitteringService(
    private val databaseService: DatabaseService,
    private val mqConsumer: MqConsumer = MqConsumer(),
) {

    private val logger = KotlinLogging.logger {  }


    fun behandleKvitteringer(){
        val kvitteringer = hentAlleKvitteringer()
        databaseService.lagreFeilkoderFraOS(kvitteringer)
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
                logger.info("Ingen flere melding fra MQ")
            }
        }while (svar != null)
        return kvitteringer
    }

    private fun varsleFeil(kvitteringer: List<TrekkTilOppdrag>) {
        TODO("Not yet implemented")
    }


}