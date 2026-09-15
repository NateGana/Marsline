package com.marsline.eip.task1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marsline.eip.broker.BrokerConfig;
import com.marsline.eip.model.BookingMessage;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * TASK 1 - MESSAGE CHANNEL
 *
 * Scenario:
 *   MARSLINE Online Booking System  --(JSON booking)-->
 *   JMS queue "marsline.booking.requests"  -->
 *   MARSLINE Booking Backend
 *
 * This is a REAL JMS message channel: the source sends the booking to
 * an embedded ActiveMQ broker over the "jms" Camel component, and a
 * separate Camel route (the target) consumes it from that same queue.
 * The checkpoint at the end is computed from what actually happened
 * (messages sent vs. messages received, and whether the JSON body
 * received matches the JSON body sent), not printed unconditionally.
 */
public class Task1MessageChannel {

    private static final Logger LOG = LoggerFactory.getLogger(Task1MessageChannel.class);
    private static final String QUEUE_NAME = "marsline.booking.requests";

    public static void run() throws Exception {

        printBanner();

        CamelContext camelContext = new DefaultCamelContext();
        BrokerConfig.registerJmsComponent(camelContext);

        List<BookingMessage> testBookings = buildTestBookings();

        CountDownLatch receivedLatch = new CountDownLatch(testBookings.size());
        Map<String, String> receivedBodiesByBookingId = new ConcurrentHashMap<>();
        Map<String, String> receivedJmsIdsByBookingId = new ConcurrentHashMap<>();

        ObjectMapper mapper = new ObjectMapper();

        camelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {

                // TARGET: MARSLINE Booking Backend
                from("jms:queue:" + QUEUE_NAME)
                        .routeId("task1-target-booking-backend")
                        .process(exchange -> {
                            String body = exchange.getIn().getBody(String.class);
                            String jmsMessageId = exchange.getIn().getHeader("JMSMessageID", String.class);

                            BookingMessage booking = mapper.readValue(body, BookingMessage.class);

                            receivedBodiesByBookingId.put(booking.getBookingId(), body);
                            receivedJmsIdsByBookingId.put(booking.getBookingId(), jmsMessageId);

                            LOG.info("[TARGET] Booking Backend received {} (JMSMessageID={})",
                                    booking.getBookingId(), jmsMessageId);

                            receivedLatch.countDown();
                        });
            }
        });

        camelContext.start();

        Map<String, String> sentBodiesByBookingId = new ConcurrentHashMap<>();
        Map<String, String> sentJmsIdsByBookingId = new ConcurrentHashMap<>();

        try {
            ProducerTemplate producer = camelContext.createProducerTemplate();

            for (BookingMessage booking : testBookings) {
                String json = mapper.writeValueAsString(booking);
                sentBodiesByBookingId.put(booking.getBookingId(), json);

                LOG.info("[SOURCE] Online Booking System sending {} ({} -> {})",
                        booking.getBookingId(), booking.getOrigin(), booking.getDestination());
                LOG.info("[SOURCE] Payload: {}", json);
                LOG.info("[QUEUE] Sending to jms:queue:{}", QUEUE_NAME);

                final String finalJson = json;
                Exchange sent = producer.send("jms:queue:" + QUEUE_NAME, exchange -> {
                    exchange.getIn().setBody(finalJson);
                    exchange.getIn().setHeader("bookingId", booking.getBookingId());
                });

                String sentJmsId = sent.getIn().getHeader("JMSMessageID", String.class);
                sentJmsIdsByBookingId.put(booking.getBookingId(), sentJmsId);
                LOG.info("[QUEUE] Accepted by broker. JMSMessageID={}", sentJmsId);
                System.out.println();
            }

            boolean allReceivedInTime = receivedLatch.await(10, TimeUnit.SECONDS);

            printCheckpoint(testBookings, sentBodiesByBookingId, receivedBodiesByBookingId,
                    sentJmsIdsByBookingId, receivedJmsIdsByBookingId, allReceivedInTime);

        } finally {
            camelContext.stop();
        }
    }

    private static List<BookingMessage> buildTestBookings() {
        List<BookingMessage> bookings = new ArrayList<>();
        bookings.add(new BookingMessage("BKG-1001", "Juan Dela Cruz", "Cabuyao", "Manila", "2026-09-15", "12A"));
        bookings.add(new BookingMessage("BKG-1002", "Maria Santos", "Cabuyao", "Batangas", "2026-09-15", "07C"));
        bookings.add(new BookingMessage("BKG-1003", "Pedro Reyes", "Cabuyao", "Quezon", "2026-09-16", "03B"));
        return Arrays.asList(bookings.toArray(new BookingMessage[0]));
    }

    private static void printBanner() {
        System.out.println();
        System.out.println("========================================");
        System.out.println("MARSLINE EIP TASK 1");
        System.out.println("MESSAGE CHANNEL CHECKPOINT");
        System.out.println("========================================");
        System.out.println();
    }

    private static void printCheckpoint(List<BookingMessage> testBookings,
                                         Map<String, String> sentBodies,
                                         Map<String, String> receivedBodies,
                                         Map<String, String> sentJmsIds,
                                         Map<String, String> receivedJmsIds,
                                         boolean allReceivedInTime) {

        int sentCount = sentBodies.size();
        int receivedCount = receivedBodies.size();

        boolean allBodiesMatch = true;
        for (BookingMessage booking : testBookings) {
            String sent = sentBodies.get(booking.getBookingId());
            String received = receivedBodies.get(booking.getBookingId());
            if (sent == null || !sent.equals(received)) {
                allBodiesMatch = false;
            }
        }

        boolean pass = allReceivedInTime
                && sentCount == testBookings.size()
                && receivedCount == testBookings.size()
                && allBodiesMatch;

        System.out.println();
        System.out.println("[CHECKPOINT]");
        System.out.println("Expected : All booking messages travel through the JMS message channel");
        System.out.println("           '" + QUEUE_NAME + "' and are received intact by the Booking Backend.");
        System.out.println("Actual   : " + receivedCount + " of " + sentCount
                + " messages received" + (allReceivedInTime ? "" : " (TIMED OUT waiting for delivery)")
                + (allBodiesMatch ? ", all payloads intact" : ", PAYLOAD MISMATCH DETECTED"));
        System.out.println();
        System.out.println("Per-message detail:");
        for (BookingMessage booking : testBookings) {
            String id = booking.getBookingId();
            boolean received = receivedBodies.containsKey(id);
            boolean bodyMatch = received && sentBodies.get(id).equals(receivedBodies.get(id));
            System.out.println("  " + id + " -> delivered=" + received
                    + ", payloadIntact=" + bodyMatch
                    + ", JMSMessageID=" + receivedJmsIds.getOrDefault(id, "N/A"));
        }
        System.out.println();
        System.out.println("Messages Sent     : " + sentCount);
        System.out.println("Messages Received : " + receivedCount);
        System.out.println("Status            : " + (pass ? "PASS" : "FAIL"));
        System.out.println("========================================");
        System.out.println();
    }
}
