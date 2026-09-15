package com.marsline.eip.task3;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marsline.eip.broker.BrokerConfig;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * TASK 3 - AGGREGATOR
 *
 * Scenario:
 *   Booking System   --(BOOKING part)--\
 *   Payment System   --(PAYMENT part)---+--> jms:queue:booking.parts --> Aggregator --> jms:queue:booking.summaries --> Fulfillment
 *   Trip Assignment  --(TRIP part)-----/
 *
 * A single MARSLINE booking is described by three independent messages
 * coming from three different backend systems. This route uses Camel's
 * real Aggregator EIP (the {@code .aggregate(...)} DSL backed by
 * {@link BookingAggregationStrategy}) to correlate those three fragments
 * by {@code bookingId} and combine them into one {@link BookingSummary}
 * that is only forwarded to Fulfillment once ALL THREE parts have
 * arrived - regardless of the order they arrive in, and regardless of
 * how many unrelated bookings are being aggregated concurrently.
 *
 * This is not string concatenation: aggregation state, correlation, and
 * completion are all handled by Camel's AggregateProcessor via the
 * {@code .aggregate(header("bookingId"), strategy)} DSL below.
 */
public class Task3Aggregator {

    private static final Logger LOG = LoggerFactory.getLogger(Task3Aggregator.class);

    private static final String QUEUE_PARTS = "booking.parts";
    private static final String QUEUE_SUMMARIES = "booking.summaries";

    /** How long the aggregator waits for more parts of a given bookingId before giving up on it. */
    private static final long COMPLETION_TIMEOUT_MS = 3000;

    public static void run() throws Exception {

        printBanner();

        CamelContext camelContext = new DefaultCamelContext();
        BrokerConfig.registerJmsComponent(camelContext);

        ObjectMapper mapper = new ObjectMapper();

        // What Fulfillment actually received, keyed by bookingId.
        Map<String, BookingSummary> fulfilledSummaries = new ConcurrentHashMap<>();
        CountDownLatch fulfillmentLatch = new CountDownLatch(2); // we expect exactly 2 COMPLETE bookings

        camelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {

                // Every incoming fragment is deserialized into a BookingPart before aggregation.
                // (Manual Jackson processing here, matching Task 1/2's style, so we don't need
                // to add the camel-jackson component as a new project dependency.)
                from("jms:queue:" + QUEUE_PARTS)
                        .routeId("task3-aggregator-route")
                        .process(exchange -> {
                            String body = exchange.getIn().getBody(String.class);
                            BookingPart part = mapper.readValue(body, BookingPart.class);
                            exchange.getIn().setBody(part);
                        })
                        .aggregate(header("bookingId"), new BookingAggregationStrategy())
                        .completionPredicate(exchange -> {
                            BookingSummary s = exchange.getIn().getBody(BookingSummary.class);
                            return s != null && s.isComplete();
                        })
                        .completionTimeout(COMPLETION_TIMEOUT_MS)
                        .process(exchange -> {
                            BookingSummary summary = exchange.getIn().getBody(BookingSummary.class);
                            if (summary.isComplete()) {
                                LOG.info("[AGGREGATOR] Correlation Key: {}", summary.getBookingId());
                                LOG.info("[AGGREGATOR] Parts: {}/3 -> Aggregation COMPLETE",
                                        summary.getPartsReceived().size());
                            } else {
                                LOG.warn("[AGGREGATOR] Correlation Key: {}", summary.getBookingId());
                                LOG.warn("[AGGREGATOR] Parts: {}/3 -> completion TIMEOUT reached, "
                                        + "booking is INCOMPLETE and will NOT be sent to {}",
                                        summary.getPartsReceived().size(), QUEUE_SUMMARIES);
                            }
                        })
                        .choice()
                            .when(exchange -> exchange.getIn().getBody(BookingSummary.class).isComplete())
                                .process(exchange -> {
                                    BookingSummary summary = exchange.getIn().getBody(BookingSummary.class);
                                    exchange.getIn().setBody(mapper.writeValueAsString(summary));
                                })
                                .to("jms:queue:" + QUEUE_SUMMARIES)
                            .otherwise()
                                .log("[AGGREGATOR] Discarding incomplete booking summary for ${body.bookingId} (not forwarded)")
                        .end();

                // FULFILLMENT: consumes only complete, aggregated booking summaries.
                from("jms:queue:" + QUEUE_SUMMARIES)
                        .routeId("task3-fulfillment")
                        .process(exchange -> {
                            String body = exchange.getIn().getBody(String.class);
                            BookingSummary summary = mapper.readValue(body, BookingSummary.class);
                            fulfilledSummaries.put(summary.getBookingId(), summary);
                            LOG.info("[OUTPUT] {} -> {}", QUEUE_SUMMARIES, summary.getBookingId());
                            fulfillmentLatch.countDown();
                        });
            }
        });

        camelContext.start();

        try {
            ProducerTemplate producer = camelContext.createProducerTemplate();
            List<BookingPart> partsInSendOrder = buildOutOfOrderTestParts();

            for (BookingPart part : partsInSendOrder) {
                String json = mapper.writeValueAsString(part);
                LOG.info("[SOURCE] Sending {} part for {}", part.getType(), part.getBookingId());
                producer.send("jms:queue:" + QUEUE_PARTS, exchange -> {
                    exchange.getIn().setBody(json);
                    exchange.getIn().setHeader("bookingId", part.getBookingId());
                });
                Thread.sleep(50); // small stagger so log ordering is readable; not required for correctness
            }

            // Wait for the 2 fully-aggregated bookings to reach Fulfillment,
            // plus extra time for the deliberately-incomplete booking's
            // completion timeout (3s) to actually fire.
            boolean bothCompleted = fulfillmentLatch.await(COMPLETION_TIMEOUT_MS + 5000, TimeUnit.MILLISECONDS);

            printCheckpoint(fulfilledSummaries, bothCompleted);

        } finally {
            camelContext.stop();
        }
    }

    /**
     * 3 bookings' worth of parts (BKG-3001, BKG-3002 - both complete) plus
     * one deliberately incomplete booking (BKG-3003 - TRIP part never sent),
     * interleaved out of order to prove the correlation key (not arrival
     * order) is what the Aggregator uses to group fragments.
     */
    private static List<BookingPart> buildOutOfOrderTestParts() {
        List<BookingPart> parts = new ArrayList<>();

        parts.add(BookingPart.payment("BKG-3001", "PAY-3001", 450.00, "PAID"));
        parts.add(BookingPart.booking("BKG-3002", "Ana Villanueva", "Cabuyao", "Lucena", "2026-09-20"));
        parts.add(BookingPart.booking("BKG-3001", "Juan Dela Cruz", "Cabuyao", "Manila", "2026-09-15"));
        parts.add(BookingPart.booking("BKG-3003", "Liza Fernandez", "Cabuyao", "Batangas", "2026-09-21"));
        parts.add(BookingPart.payment("BKG-3003", "PAY-3003", 300.00, "PAID"));
        // NOTE: BKG-3003's TRIP part is intentionally never sent, so its
        // aggregation should time out INCOMPLETE and must never reach
        // booking.summaries / Fulfillment.
        parts.add(BookingPart.payment("BKG-3002", "PAY-3002", 520.00, "PAID"));
        parts.add(BookingPart.trip("BKG-3001", "TRIP-3001", "MAR-101", "12A"));
        parts.add(BookingPart.trip("BKG-3002", "TRIP-3002", "MAR-102", "05C"));

        return parts;
    }

    private static void printBanner() {
        System.out.println();
        System.out.println("========================================");
        System.out.println("MARSLINE EIP TASK 3");
        System.out.println("AGGREGATOR CHECKPOINT");
        System.out.println("========================================");
        System.out.println();
    }

    private static void printCheckpoint(Map<String, BookingSummary> fulfilledSummaries, boolean bothCompleted) {

        Map<String, BookingSummary> ordered = new LinkedHashMap<>(fulfilledSummaries);

        boolean bkg3001Complete = ordered.containsKey("BKG-3001") && ordered.get("BKG-3001").isComplete();
        boolean bkg3002Complete = ordered.containsKey("BKG-3002") && ordered.get("BKG-3002").isComplete();
        boolean bkg3003NotLeaked = !ordered.containsKey("BKG-3003"); // must NOT have reached Fulfillment

        boolean pass = bothCompleted && bkg3001Complete && bkg3002Complete && bkg3003NotLeaked;

        System.out.println();
        System.out.println("[CHECKPOINT]");
        System.out.println("Expected : Related BOOKING/PAYMENT/TRIP messages are combined by bookingId into");
        System.out.println("           one booking.summaries message per COMPLETE booking. An incomplete");
        System.out.println("           booking (missing a part) must never produce a false-complete summary.");
        System.out.println();
        System.out.println("Actual   : " + ordered.size() + " booking(s) reached Fulfillment via " + QUEUE_SUMMARIES + ":");
        for (BookingSummary s : ordered.values()) {
            System.out.println("  " + s.getBookingId() + " -> parts=" + s.getPartsReceived()
                    + ", complete=" + s.isComplete());
        }
        System.out.println("  BKG-3003 (deliberately incomplete, TRIP part withheld) reached Fulfillment: "
                + !bkg3003NotLeaked);
        System.out.println();
        System.out.println("Bookings successfully aggregated : " + ordered.size() + "/2 expected complete bookings");
        System.out.println("Incomplete booking correctly excluded from summaries: " + bkg3003NotLeaked);
        System.out.println("Status            : " + (pass ? "PASS" : "FAIL"));
        System.out.println("========================================");
        System.out.println();
    }
}
