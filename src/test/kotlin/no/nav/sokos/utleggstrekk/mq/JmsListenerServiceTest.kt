package no.nav.sokos.utleggstrekk.mq

import io.kotest.core.spec.style.BehaviorSpec
import no.nav.sokos.utleggstrekk.config.PropertiesConfig
import no.nav.sokos.utleggstrekk.listener.MQListener
import no.nav.sokos.utleggstrekk.util.resourceToString
import org.apache.activemq.artemis.jms.client.ActiveMQQueue

class JmsListenerServiceTest :
    BehaviorSpec({

        extensions(listOf(MQListener))

        val mqProps = PropertiesConfig.MQProperties()
        val kvitteringConsumerService: JmsListenerService by lazy {
            JmsListenerService(
                osKvitteringQueue = ActiveMQQueue(mqProps.replyQueueName),
                connectionFactory = MQListener.connectionFactory,
            )
        }

        val jmsProducerService: JmsProducerService by lazy {
            JmsProducerService(
                senderQueue = ActiveMQQueue(mqProps.replyQueueName),
                replyQueue = ActiveMQQueue(mqProps.replyQueueName),
                connectionFactory = MQListener.connectionFactory,
            )
        }

        Given("Oppdrag sender kvittering på et trekk") {

            val reply = resourceToString("mq/trekk_ok_kvittering.json")
            jmsProducerService.send(reply)

            When("kvittering er mottatt") {
                Then("skal kvittering lagres i database") {
                }
            }
        }
    })
