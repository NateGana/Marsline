package com.marsline.eip.task2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marsline.eip.broker.BrokerConfig;
import com.marsline.eip.model.BookingMessage;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * TASK 2 - CONTENT-BASED ROUTER
 *
 * Scenario:
 *   MARSLINE Booking Intake  --(JSON booking)-->
 *   JMS queue "marsline.booking.inbound"  -->
 *   Content-Based Router (reads the "destination" field)  -->
 *       destination is Metro Manila  --> "marsline.booking.local"       --> Local/Metro Processing System
 *       destination is NOT Metro Manila --> "marsline.booking.provincial" --> Provincial Processing System
 *
 * Routing rule (documented, not hard-coded to one branch):
 *   A booking is classified LOCAL only if its destination matches one of
 *   the Metro Manila cities in LOCAL_METRO_DESTINATIONS below (case
 *   insensitive). Every other destination is classified PROVINCIAL.
 *   Note: "Quezon" (the province) is intentionally NOT the same as
 *   "Quezon City" (a Metro Manila city) - this project treats them as
 *   two different, correctly distinguished destinations.
 *
 * This is a REAL content-based route: the classification is computed
 * from the actual "destination" field of each JSON message inside a
 * Camel .choice(), and each branch is delivered through its own real
 * JMS queue to its own real consumer route.
 */
public class Task2ContentBasedRouter {

    private static final Logger LOG = LoggerFactory.getLogger(Task2ContentBasedRouter.class);

    private static final String QUEUE_INBOUND = "marsline.booking.inbound";
    private static final String QUEUE_LOCAL = "marsline.booking.local";
    private static final String QUEUE_PROVINCIAL = "marsline.booking.provincial";

    private static final Set<String> LOCAL_METRO_DESTINATIONS = Set.of(
            "manila", "makati", "quezon city", "pasig", "taguig",
            "mandaluyong", "pasay", "paranaque", "caloocan"
    );

    public static void run() throws Exception {

        printBanner();

        CamelContext camelContext = new DefaultCamelContext();
        BrokerConfig.registerJmsComponent(camelContext);

        List<TestCase> testCases = buildTestCases();

        CountDownLatch receivedLatch = new CountDownLatch(testCases.size());
        List<String> localReceived = Collections.synchronizedList(new ArrayList<>());
        List<String> provincialReceived = Collections.synchronizedList(new ArrayList<>());

        ObjectMapper mapper = new ObjectMapper();

        camelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {

                // CONTENT-BASED ROUTER
                from("jms:queue:" + QUEUE_INBOUND)
                        .routeId("task2-content-based-router")
                        .process(exchange -> {
                            String body = exchange.getIn().getBody(String.class);
                            BookingMessage booking = mapper.readValue(body, BookingMessage.class);

                            String classification = classify(booking.getDestination());

                            exchange.getIn().setHeader("bookingId", booking.getBookingId());
                            exchange.getIn().setHeader("classification", classification);

                            LOG.info("[INPUT] {}", booking.getBookingId());
                            LOG.info("[INPUT] Destination: {}", booking.getDestination());
                            LOG.info("[ROUTER] Destination matched {}", classification);
                        })
                        .choice()
                            .when(header("classification").isEqualTo("LOCAL"))
                                .log("[OUTPUT QUEUE] " + QUEUE_LOCAL)
                                .to("jms:queue:" + QUEUE_LOCAL)
                            .otherwise()
                                .log("[OUTPUT QUEUE] " + QUEUE_PROVINCIAL)
                                .to("jms:queue:" + QUEUE_PROVINCIAL)
                        .end();

                // TARGET: Local / Metro Processing System
                from("jms:queue:" + QUEUE_LOCAL)
                        .routeId("task2-local-system")
                        .process(exchange -> {
                            String bookingId = exchange.getIn().getHeader("bookingId", String.class);
                            localReceived.add(bookingId);
                            LOG.info("[TARGET] Local/Metro Processing System received {}", bookingId);
                            receivedLatch.countDown();
                        });

                // TARGET: Provincial Processing System
                from("jms:queue:" + QUEUE_PROVINCIAL)
                        .routeId("task2-provincial-system")
                        .process(exchange -> {
                            String bookingId = exchange.getIn().getHeader("bookingId", String.class);
                            provincialReceived.add(bookingId);
                            LOG.info("[TARGET] Provincial Processing System received {}", bookingId);
                            receivedLatch.countDown();
                        });
            }
        });

        camelContext.start();

        try {
            ProducerTemplate producer = camelContext.createProducerTemplate();

            for (TestCase tc : testCases) {
                String json = mapper.writeValueAsString(tc.booking);
                producer.sendBody("jms:queue:" + QUEUE_INBOUND, json);
                System.out.println();
                Thread.sleep(150); // small stagger so console/log ordering stays readable for screenshots
            }

            boolean allReceivedInTime = receivedLatch.await(10, TimeUnit.SECONDS);

            printCheckpoint(testCases, localReceived, provincialReceived, allReceivedInTime);

        } finally {
            camelContext.stop();
        }
    }

    /**
     * The actual routing rule used by the Content-Based Router route above.
     * Kept as its own method so the rule is easy to point to when writing
     * up the checkpoint documentation.
     */
    private static String classify(String destination) {
        if (destination == null) {
            return "PROVINCIAL";
        }
        return LOCAL_METRO_DESTINATIONS.contains(destination.trim().toLowerCase())
                ? "LOCAL"
                : "PROVINCIAL";
    }

    private static List<TestCase> buildTestCases() {
        List<TestCase> cases = new ArrayList<>();
        cases.add(new TestCase(
                new BookingMessage("BKG-2001", "Ana Villanueva", "Cabuyao", "Manila", "2026-09-17", "10A"),
                "LOCAL"));
        cases.add(new TestCase(
                new BookingMessage("BKG-2002", "Carlo Ramos", "Cabuyao", "Makati", "2026-09-17", "05B"),
                "LOCAL"));
        cases.add(new TestCase(
                new BookingMessage("BKG-2003", "Liza Fernandez", "Cabuyao", "Batangas", "2026-09-17", "14C"),
                "PROVINCIAL"));
        cases.add(new TestCase(
                new BookingMessage("BKG-2004", "Noel Aquino", "Cabuyao", "Quezon", "2026-09-18", "02A"),
                "PROVINCIAL"));
        cases.add(new TestCase(
                new BookingMessage("BKG-2005", "Grace Lim", "Cabuyao", "Pampanga", "2026-09-18", "08D"),
                "PROVINCIAL"));
        return cases;
    }

    private static void printBanner() {
        System.out.println();
        System.out.println("========================================");
        System.out.println("MARSLINE EIP TASK 2");
        System.out.println("CONTENT-BASED ROUTER CHECKPOINT");
        System.out.println("========================================");
        System.out.println();
        System.out.println("Routing rule: destination in Metro Manila (Manila, Makati, Quezon City,");
        System.out.println("Pasig, Taguig, Mandaluyong, Pasay, Paranaque, Caloocan) -> LOCAL.");
        System.out.println("Every other destination -> PROVINCIAL.");
        System.out.println();
    }

    private static void printCheckpoint(List<TestCase> testCases,
                                         List<String> localReceived,
                                         List<String> provincialReceived,
                                         boolean allReceivedInTime) {

        int expectedLocal = 0;
        int expectedProvincial = 0;
        int correctCount = 0;

        for (TestCase tc : testCases) {
            String id = tc.booking.getBookingId();
            boolean expectedIsLocal = tc.expectedClassification.equals("LOCAL");
            if (expectedIsLocal) {
                expectedLocal++;
            } else {
                expectedProvincial++;
            }

            boolean actuallyLocal = localReceived.contains(id);
            boolean actuallyProvincial = provincialReceived.contains(id);
            boolean correct = (expectedIsLocal && actuallyLocal) || (!expectedIsLocal && actuallyProvincial);
            if (correct) {
                correctCount++;
            }
        }

        int totalReceived = localReceived.size() + provincialReceived.size();
        boolean pass = allReceivedInTime
                && totalReceived == testCases.size()
                && correctCount == testCases.size();

        System.out.println();
        System.out.println("[CHECKPOINT]");
        System.out.println("Expected : Bookings are routed to LOCAL or PROVINCIAL according to destination.");
        System.out.println("Actual   : " + correctCount + "/" + testCases.size()
                + " bookings reached the correct branch"
                + (allReceivedInTime ? "" : " (TIMED OUT waiting for one or more deliveries)"));
        System.out.println();
        System.out.println("Per-message detail:");
        for (TestCase tc : testCases) {
            String id = tc.booking.getBookingId();
            boolean actuallyLocal = localReceived.contains(id);
            boolean actuallyProvincial = provincialReceived.contains(id);
            String actual = actuallyLocal ? "LOCAL" : (actuallyProvincial ? "PROVINCIAL" : "NOT RECEIVED");
            boolean correct = actual.equals(tc.expectedClassification);
            System.out.println("  " + id + " (" + tc.booking.getDestination() + ") -> expected="
                    + tc.expectedClassification + ", actual=" + actual + ", correct=" + correct);
        }
        System.out.println();
        System.out.println("Local        : " + localReceived.size() + " (expected " + expectedLocal + ")");
        System.out.println("Provincial   : " + provincialReceived.size() + " (expected " + expectedProvincial + ")");
        System.out.println("Status       : " + (pass ? "PASS" : "FAIL"));
        System.out.println("========================================");
        System.out.println();
    }

    /** Pairs a test booking with the classification we expect the router to assign it. */
    private static class TestCase {
        final BookingMessage booking;
        final String expectedClassification;

        TestCase(BookingMessage booking, String expectedClassification) {
            this.booking = booking;
            this.expectedClassification = expectedClassification;
        }
    }
}
