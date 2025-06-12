package no.nav.sokos.utleggstrekk.service

import io.kotest.core.spec.style.FunSpec
import io.mockk.mockk
import no.nav.sokos.utleggstrekk.mq.MqConsumer

class KvitteringServiceTest : FunSpec(
    {
        val databaseServiceMock = mockk<DatabaseService>()
        val mqConsumer = mockk<MqConsumer>()

        val kvitteringService = KvitteringService(databaseServiceMock, mqConsumer)

        test("hei") {
//            //val kvitteringer = resourceToStringList("DiverseTestDokumenterKvitteringer_fraOs.txt").also { it.forEach { s -> println(s) }}
//            val kvitteringer = resourceToStringList("Kvitteringer1.txt").also { it.forEach { s -> println(s) }}
//            println("Kvitteringer ant: ${kvitteringer.size}")
//            every { mqConsumer.receive()} returnsMany   kvitteringer  andThenAnswer  { nothing }
//            val result = kvitteringService.hentAlleKvitteringer()
//            println("meldinger fra MQ ant: ${result.size}")


        }
})
