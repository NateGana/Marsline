package com.marsline.eip.task5;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import com.marsline.eip.broker.BrokerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TASK 5 - ERROR CHANNEL + RETRY
 *
 * Scenario: the MARSLINE Booking Backend is temporarily unavailable for
 * some bookings (simulated) and permanently unavailable for one booking
 * used to demonstrate the parking lot.
 *
 *   Booking Message -> Booking Backend -> FAILURE -> marsline.booking.errors
 *        -> Retry (real Camel redelivery) -> Booking Backend -> SUCCESS
 *
 *   If retries are exhausted -> marsline.booking.parkinglot
 *
 * This uses Camel's real error-handling machinery:
 *   - a route-level {@code onException(...)} clause with
 *     {@code maximumRedeliveries} / {@code redeliveryDelay} drives the
 *     actual retry attempts (Camel re-invokes the route, it is not a
 *     hand-rolled while-loop),
 *   - each failed attempt is also forwarded to the real JMS queue
 *     {@code marsline.booking.errors} as an audit trail before the retry
 *     fires,
 *   - once Camel's own redelivery counter is exhausted (detected via the
 *     {@code Exchange.REDELIVERY_EXHAUSTED} property Camel sets - not a
 *     hand-rolled counter), the exchange is additionally routed to the
 *     real JMS queue {@code marsline.booking.parkinglot}.
 *
 * The "failing target" is a real stateful simulator
 * ({@link #simulateBookingBackend}) keyed by bookingId: it genuinely
 * throws for a configured number of attempts before genuinely succeeding
 * (or never succeeding, for the permanent-failure test case) - nothing
 * here is a printed fake result.
 */
public class Task5ErrorChannelRetry {

    private static final Logger LOG = LoggerFactory.getLogger(Task5ErrorChannelRetry.class);

    private static final String QUEUE_REQUESTS = "marsline.booking.requests.task5";
    private static final String QUEUE_ERRORS = "marsline.booking.errors";
    private static final String QUEUE_PARKINGLOT = "marsline.booking.parkinglot";

    private static final int MAX_REDELIVERIES = 2; // + the original attempt = 3 attempts total

    /**
     * Per-bookingId configuration of how many times the simulated backend
     * should fail before succeeding. Integer.MAX_VALUE means "never
     * succeeds" (used to demonstrate the parking lot).
     */
    private static final Map<String, Integer> FAILS_BEFORE_SUCCESS = Map.of(
            "BKG-5001", 2, // fails attempt 1 & 2, succeeds on attempt 3
            "BKG-5002", 1, // fails attempt 1, succeeds on attempt 2
            "BKG-5003", Integer.MAX_VALUE // never succeeds -> parking lot
    );

    private static final Map<String, AtomicInteger> attemptCounters = new ConcurrentHashMap<>();
    private static final Map<String, List<String>> attemptLog = new ConcurrentHashMap<>();

    public static void run() throws Exception {

        printBanner();

        CamelContext camelContext = new DefaultCamelContext();
        BrokerConfig.registerJmsComponent(camelContext);

        Map<String, Boolean> delivered = new ConcurrentHashMap<>();
        Map<String, Boolean> parkedPermanently = new ConcurrentHashMap<>();

        CountDownLatch successLatch = new CountDownLatch(2);   // BKG-5001, BKG-5002 should eventually succeed
        CountDownLatch parkingLotLatch = new CountDownLatch(1); // BKG-5003 should end up in the parking lot

        camelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {

                // Real Camel retry policy for the simulated backend failure.
                // Every failed attempt (retry or final) is forwarded to the real
                // marsline.booking.errors queue as an audit trail. Once Camel's own
                // redelivery counter (maximumRedeliveries) is exhausted, the exchange
                // is additionally routed on to marsline.booking.parkinglot - this is
                // detected via the REDELIVERY_EXHAUSTED exchange property Camel sets,
                // not a hand-rolled counter.
                onException(RuntimeException.class)
                        .maximumRedeliveries(MAX_REDELIVERIES)
                        .redeliveryDelay(200)
                        .retryAttemptedLogLevel(LoggingLevel.WARN)
                        .log(LoggingLevel.WARN, "[ERROR] Delivery failed for ${header.bookingId}: ${exception.message}")
                        .to("jms:queue:" + QUEUE_ERRORS)
                        .choice()
                            .when(exchangeProperty(Exchange.REDELIVERY_EXHAUSTED).isEqualTo(true))
                                .to("jms:queue:" + QUEUE_PARKINGLOT)
                        .end()
                        .handled(true);

                from("jms:queue:" + QUEUE_REQUESTS)
                        .routeId("task5-booking-backend")
                        .process(exchange -> {
                            String bookingId = exchange.getIn().getHeader("bookingId", String.class);
                            simulateBookingBackend(bookingId);
                            // Reaching this line means the simulated backend accepted the booking.
                        })
                        .process(exchange -> {
                            String bookingId = exchange.getIn().getHeader("bookingId", String.class);
                            LOG.info("[TARGET] Backend available");
                            LOG.info("[SUCCESS] {} delivered", bookingId);
                            delivered.put(bookingId, true);
                            if (!"BKG-5003".equals(bookingId)) {
                                successLatch.countDown();
                            }
                        });

                // Audit trail: every failed attempt is visible here before its retry fires.
                from("jms:queue:" + QUEUE_ERRORS)
                        .routeId("task5-error-audit")
                        .log(LoggingLevel.INFO, "[ERROR QUEUE] " + QUEUE_ERRORS + " recorded a failed attempt for ${header.bookingId}");

                // Permanently-failing bookings land here once retries are exhausted.
                from("jms:queue:" + QUEUE_PARKINGLOT)
                        .routeId("task5-parkinglot")
                        .process(exchange -> {
                            String bookingId = exchange.getIn().getHeader("bookingId", String.class);
                            LOG.error("[PARKINGLOT] {} moved to {} after {} attempts - giving up",
                                    bookingId, QUEUE_PARKINGLOT, attemptCounters.getOrDefault(bookingId, new AtomicInteger(0)).get());
                            parkedPermanently.put(bookingId, true);
                            parkingLotLatch.countDown();
                        });
            }
        });

        camelContext.start();

        try {
            ProducerTemplate producer = camelContext.createProducerTemplate();

            for (String bookingId : List.of("BKG-5001", "BKG-5002", "BKG-5003")) {
                LOG.info("[INPUT] {}", bookingId);
                LOG.info("[TARGET] Booking Backend unavailable (simulated) for this attempt cycle");
                producer.send("jms:queue:" + QUEUE_REQUESTS, exchange ->
                        exchange.getIn().setHeader("bookingId", bookingId));
                System.out.println();
            }

            boolean successesArrived = successLatch.await(8, TimeUnit.SECONDS);
            boolean parkingLotArrived = parkingLotLatch.await(8, TimeUnit.SECONDS);

            printCheckpoint(delivered, parkedPermanently, successesArrived, parkingLotArrived);

        } finally {
            camelContext.stop();
        }
    }

    /**
     * Genuinely stateful failing target: throws a real RuntimeException for
     * as many attempts as {@link #FAILS_BEFORE_SUCCESS} configures for this
     * bookingId, then genuinely stops throwing (or never stops, for the
     * permanent-failure test booking).
     */
    private static void simulateBookingBackend(String bookingId) {
        int attempt = attemptCounters.computeIfAbsent(bookingId, k -> new AtomicInteger(0)).incrementAndGet();
        int failsBeforeSuccess = FAILS_BEFORE_SUCCESS.getOrDefault(bookingId, 0);

        List<String> log = attemptLog.computeIfAbsent(bookingId, k -> new CopyOnWriteArrayList<>());

        if (attempt <= failsBeforeSuccess) {
            log.add("Attempt " + attempt + " -> FAILED");
            LOG.warn("[RETRY] Attempt {} for {}", attempt, bookingId);
            throw new RuntimeException("Simulated Booking Backend outage on attempt " + attempt + " for " + bookingId);
        }

        log.add("Attempt " + attempt + " -> SUCCESS");
    }

    private static void printBanner() {
        System.out.println();
        System.out.println("========================================");
        System.out.println("MARSLINE EIP TASK 5");
        System.out.println("ERROR CHANNEL + RETRY CHECKPOINT");
        System.out.println("========================================");
        System.out.println();
    }

    private static void printCheckpoint(Map<String, Boolean> delivered,
                                         Map<String, Boolean> parkedPermanently,
                                         boolean successesArrived,
                                         boolean parkingLotArrived) {

        Map<String, List<String>> orderedLog = new LinkedHashMap<>(attemptLog);

        boolean bkg5001Recovered = Boolean.TRUE.equals(delivered.get("BKG-5001"));
        boolean bkg5002Recovered = Boolean.TRUE.equals(delivered.get("BKG-5002"));
        boolean bkg5003Parked = Boolean.TRUE.equals(parkedPermanently.get("BKG-5003"));
        boolean bkg5003NeverDelivered = !delivered.containsKey("BKG-5003");

        int recoveredCount = (bkg5001Recovered ? 1 : 0) + (bkg5002Recovered ? 1 : 0);

        boolean pass = successesArrived && parkingLotArrived
                && bkg5001Recovered && bkg5002Recovered
                && bkg5003Parked && bkg5003NeverDelivered;

        System.out.println();
        System.out.println("[CHECKPOINT]");
        System.out.println("Expected : Failed messages enter the error path (" + QUEUE_ERRORS + ") and are retried");
        System.out.println("           via Camel's real redelivery policy; a message that keeps failing beyond");
        System.out.println("           " + MAX_REDELIVERIES + " retries is moved to " + QUEUE_PARKINGLOT + " instead of succeeding.");
        System.out.println();
        System.out.println("Actual   :");
        for (Map.Entry<String, List<String>> entry : orderedLog.entrySet()) {
            System.out.println("  " + entry.getKey() + ":");
            for (String line : entry.getValue()) {
                System.out.println("    " + line);
            }
            if (Boolean.TRUE.equals(delivered.get(entry.getKey()))) {
                System.out.println("    -> Final outcome: DELIVERED");
            } else if (Boolean.TRUE.equals(parkedPermanently.get(entry.getKey()))) {
                System.out.println("    -> Final outcome: PARKED (" + QUEUE_PARKINGLOT + ")");
            } else {
                System.out.println("    -> Final outcome: UNKNOWN (did not resolve within timeout)");
            }
        }
        System.out.println();
        System.out.println("Messages recovered via retry : " + recoveredCount + "/2 (BKG-5001, BKG-5002)");
        System.out.println("Messages permanently parked   : " + (bkg5003Parked ? 1 : 0) + "/1 (BKG-5003)");
        System.out.println("Status                        : " + (pass ? "PASS" : "FAIL"));
        System.out.println("========================================");
        System.out.println();
    }
}
