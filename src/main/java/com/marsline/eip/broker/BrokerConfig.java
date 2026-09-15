package com.marsline.eip.broker;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.component.jms.JmsComponent;

/**
 * Wires an embedded ActiveMQ "Classic" broker into a Camel context.
 *
 * Using the vm:// transport means the broker lives INSIDE this JVM.
 * There is no separate ActiveMQ server to install, start, or configure
 * on Windows - running the Java application is enough.
 *
 * broker.persistent=false means no files are written to disk for
 * message storage; every run starts from a clean broker, which is
 * exactly what we want for a repeatable lab demonstration.
 */
public final class BrokerConfig {

    /** Name of the in-JVM broker instance shared by every task. */
    public static final String BROKER_URL =
            "vm://marsline-broker?broker.persistent=false&broker.useJmx=false";

    private BrokerConfig() {
        // utility class
    }

    /**
     * Registers the "jms" Camel component backed by the embedded broker.
     * After calling this, routes can use endpoints like:
     *   jms:queue:marsline.booking.requests
     */
    public static void registerJmsComponent(CamelContext camelContext) {
        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(BROKER_URL);
        camelContext.addComponent("jms", JmsComponent.jmsComponentAutoAcknowledge(connectionFactory));
    }
}
