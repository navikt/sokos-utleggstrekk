package no.nav.sokos.utleggstrekk.listener

import io.kotest.core.listeners.TestListener
import io.kotest.core.spec.Spec
import jakarta.jms.ConnectionFactory
import org.apache.activemq.artemis.api.core.TransportConfiguration
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMAcceptorFactory
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory

object MQListener : TestListener {
    private val server =
        EmbeddedActiveMQ()
            .setConfiguration(
                ConfigurationImpl()
                    .setPersistenceEnabled(false)
                    .setSecurityEnabled(false)
                    .addAcceptorConfiguration(TransportConfiguration(InVMAcceptorFactory::class.java.name)),
            )
    lateinit var connectionFactory: ConnectionFactory

    override suspend fun beforeSpec(spec: Spec) {
        server.start()
        connectionFactory = ActiveMQConnectionFactory("vm:localhost?create=false")
    }
}
