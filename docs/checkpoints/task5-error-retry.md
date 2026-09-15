# Task 5 Checkpoint — Error Channel + Retry

**Group:** MARSLINE
**Members (alphabetical):** Baculinao, Mark Joseph L. · Bernal, Mars Jairus G. · Bunao, Ron Rupert M. · Butin, Josel Reimar M. · Gana, Nathaniel E.

## Objective
Demonstrate a working Error Channel + Retry: a booking that fails to
reach the MARSLINE Booking Backend is genuinely retried using Camel's
real redelivery mechanism, with failed attempts visible on a dedicated
error queue, and a message that keeps failing beyond the retry limit is
moved to a parking lot instead of being silently dropped or falsely
reported as delivered.

## MARSLINE Scenario
The MARSLINE Booking Backend occasionally experiences brief outages
(simulated here). Bookings sent during an outage should not be lost —
they should be retried automatically, and if a specific booking never
succeeds, it should be set aside (parking lot) for manual follow-up
instead of blocking everything else.

## Architecture
```
Booking Message
      |
      v
Booking Backend  (simulated - genuinely throws for a configured
      |            number of attempts per bookingId)
      | FAILURE
      v
marsline.booking.errors   (real JMS queue - audit trail, one entry per failed attempt)
      |
      v
Retry (Camel onException + maximumRedeliveries/redeliveryDelay - real redelivery, not a hand-rolled loop)
      |
      v
Booking Backend
      |
      +-- SUCCESS -> booking delivered
      |
      +-- retries exhausted (Exchange.REDELIVERY_EXHAUSTED) -->
                marsline.booking.parkinglot   (real JMS queue)
```

## EIP Being Demonstrated
**Error Channel + Retry** — implemented with Camel's real
`onException(RuntimeException.class).maximumRedeliveries(...).redeliveryDelay(...)`
error-handling DSL. Each failed attempt is forwarded to a real
`marsline.booking.errors` JMS queue, and exhausted messages are routed
to a real `marsline.booking.parkinglot` JMS queue. Nothing here prints
"retry successful" without an actual exception having been thrown and
an actual subsequent attempt having actually succeeded.

## Components / Queues / Classes Used
- `marsline.booking.requests.task5` — inbound queue for bookings to be delivered
- `marsline.booking.errors` — real JMS queue recording every failed delivery attempt
- `marsline.booking.parkinglot` — real JMS queue for permanently-failing bookings
- `com.marsline.eip.task5.Task5ErrorChannelRetry` — route, failing-backend simulator, and checkpoint

## Failure Scenario / Retry Behavior
A stateful simulator (`simulateBookingBackend`) is configured per test bookingId:

| Booking ID | Configured behavior | Expected outcome |
|---|---|---|
| BKG-5001 | Fails attempts 1 & 2, succeeds on attempt 3 | Recovered via retry |
| BKG-5002 | Fails attempt 1, succeeds on attempt 2 | Recovered via retry |
| BKG-5003 | Always fails | Exhausts retries, moved to parking lot |

`maximumRedeliveries(2)` means each booking gets up to 3 total attempts
(1 original + 2 retries) before Camel considers redelivery exhausted.

## Parking Lot Behavior
When `onException`'s redeliveries are exhausted, Camel sets the
`Exchange.REDELIVERY_EXHAUSTED` property to `true`. The route checks
this property and, only in that case, forwards the exchange to
`marsline.booking.parkinglot`, where a dedicated consumer route logs the
permanent failure. This is the real exhaustion signal from Camel's
redelivery machinery, not a manually counted "if attempts > 3" check.

## Test Data
3 bookings: BKG-5001, BKG-5002 (both should recover), BKG-5003 (should
permanently fail and be parked).

## Expected Result
- BKG-5001: FAILED (attempt 1) → FAILED (attempt 2) → SUCCESS (attempt 3) → delivered.
- BKG-5002: FAILED (attempt 1) → SUCCESS (attempt 2) → delivered.
- BKG-5003: FAILED (attempts 1, 2, 3) → retries exhausted → moved to `marsline.booking.parkinglot`.
- 2 of 3 messages recovered via retry; 1 of 3 permanently parked.

## Screenshot Instructions
Capture the full console output of:
```
mvn compile exec:java -Dexec.args="task5"
```
showing, per booking, the `[RETRY] Attempt n for BKG-50xx` lines, the
`[ERROR QUEUE] marsline.booking.errors recorded a failed attempt ...` lines, the
`[SUCCESS] BKG-50xx delivered` or `[PARKINGLOT] BKG-50xx moved to ...` line, and
the final `[CHECKPOINT]` block with its recovered/parked counts and `Status` line.

## Actual Result
**NOT YET TESTED — this Claude session has no Maven/JDK-with-dependencies/internet
access to Maven Central to compile or run this project.** The error-handling route
has been written and manually reviewed (uses Camel's built-in `onException` /
redelivery DSL and only the JMS queues already supported by the existing
`BrokerConfig`), but it has not been executed. Windows testing is required before
this can be marked PASS or FAIL.

## Status
**IMPLEMENTED — WINDOWS TEST PENDING**
