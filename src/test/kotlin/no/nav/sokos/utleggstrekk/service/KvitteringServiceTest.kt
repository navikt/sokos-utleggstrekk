package no.nav.sokos.utleggstrekk.service

import io.kotest.core.spec.style.FunSpec
import io.mockk.mockk
import no.nav.sokos.utleggstrekk.mq.MqConsumer

class KvitteringServiceTest : FunSpec(
    {
        val databaseServiceMock = mockk<DatabaseService>()
        val mqConsumer = mockk<MqConsumer>()

        val kvitteringService = KvitteringService(databaseServiceMock, mqConsumer)

})
