package no.nav.sokos.utleggstrekk.service

import no.nav.sokos.utleggstrekk.mq.MqConsumer

class KvitteringService(
    private val databaseService: DatabaseService,
    private val mqConsumer: MqConsumer = MqConsumer(),
) {

    fun hentAlleKvitteringer():List<String>{
        return mqConsumer.getKvitteringFromMQ()
    }
}