# Task 3 Checkpoint — Aggregator

**Group:** MARSLINE
**Members (alphabetical):** Baculinao, Mark Joseph L. · Bernal, Mars Jairus G. · Bunao, Ron Rupert M. · Butin, Josel Reimar M. · Gana, Nathaniel E.

## Objective
Demonstrate a working Aggregator: three independent messages describing
one MARSLINE booking (from the Booking System, the Payment System, and
the Trip Assignment System) are combined into a single booking summary,
correlated by `bookingId`, using Apache Camel's real Aggregator EIP.

## MARSLINE Scenario
A passenger's booking is not fully known from any single system. The
Booking System knows *who* and *where*, the Payment System knows *how
much and whether it was paid*, and the Trip Assignment System knows
*which bus and seat*. Fulfillment (e.g. printing/issuing the final
ticket) needs all three pieces before it can act.

## Architecture
```
Booking System   --(BOOKING part)--\
Payment System   --(PAYMENT part)---+--> jms:queue:booking.parts
Trip Assignment  --(TRIP part)-----/                |
                                                     v
                                              Aggregator
                                   (Camel .aggregate(), correlated
                                    by header "bookingId")
                                                     |
                                     complete (3/3)? |
                                          yes ------>| no -> discarded,
                                                     v        never sent
                                       jms:queue:booking.summaries
                                                     |
                                                     v
                                              Fulfillment
```

## EIP Being Demonstrated
**Aggregator** — implemented with Camel's actual `.aggregate(header("bookingId"), strategy)`
DSL and a custom `org.apache.camel.AggregationStrategy`
(`BookingAggregationStrategy`), not manual string concatenation.

## Components / Queues / Classes Used
- `jms:queue:booking.parts` — inbound queue carrying individual BOOKING / PAYMENT / TRIP fragments
- `jms:queue:booking.summaries` — outbound queue carrying only *complete* aggregated summaries
- `com.marsline.eip.task3.BookingPart` — union-shaped fragment model (type + bookingId + type-specific fields)
- `com.marsline.eip.task3.BookingSummary` — the accumulated result; tracks which part types have arrived and whether it is `isComplete()`
- `com.marsline.eip.task3.BookingAggregationStrategy` — real `AggregationStrategy` merging fragments by `bookingId`
- `com.marsline.eip.task3.Task3Aggregator` — wires the route, sends test data, prints the checkpoint

## How It Works
1. Each fragment is published as JSON to `booking.parts` with a JMS header `bookingId`.
2. Camel's `.aggregate(header("bookingId"), new BookingAggregationStrategy())` groups
   fragments by that header — a completely separate accumulator is kept per distinct
   `bookingId`, so interleaved/out-of-order fragments for different bookings can never
   be mixed together.
3. `.completionPredicate(...)` marks a group complete once its `BookingSummary` has
   received all three part types (`BOOKING`, `PAYMENT`, `TRIP`).
4. `.completionTimeout(3000)` flushes a group that never completes (missing a part)
   after 3 seconds, so the route doesn't wait forever — but a flushed *incomplete*
   summary is explicitly **not** forwarded to `booking.summaries` (checked via
   `summary.isComplete()` right before the `.to(...)`).
5. Fulfillment consumes `booking.summaries` and only ever sees fully-merged bookings.

## Test Data
Three booking IDs, fragments interleaved out of order:

| Order sent | Booking ID | Part type | Note |
|---|---|---|---|
| 1 | BKG-3001 | PAYMENT | |
| 2 | BKG-3002 | BOOKING | |
| 3 | BKG-3001 | BOOKING | |
| 4 | BKG-3003 | BOOKING | |
| 5 | BKG-3003 | PAYMENT | TRIP part for BKG-3003 is **never sent** (intentional) |
| 6 | BKG-3002 | PAYMENT | |
| 7 | BKG-3001 | TRIP | completes BKG-3001 (3/3) |
| 8 | BKG-3002 | TRIP | completes BKG-3002 (3/3) |

- BKG-3001 and BKG-3002: all 3 parts sent, out of order → expected to **complete**.
- BKG-3003: only BOOKING + PAYMENT sent → expected to **time out incomplete** and
  must never reach `booking.summaries`.

## Expected Result
- BKG-3001 → complete, forwarded to `booking.summaries`, received by Fulfillment.
- BKG-3002 → complete, forwarded to `booking.summaries`, received by Fulfillment.
- BKG-3003 → times out at 2/3 parts, correctly discarded, never reaches Fulfillment.

## Screenshot Instructions
Capture the full console output of:
```
mvn compile exec:java -Dexec.args="task3"
```
showing (a) each `[AGGREGATOR] Received ... for BKG-30xx (n/3)` line, (b) the
`[AGGREGATOR] Correlation Key ... Aggregation COMPLETE` / `TIMEOUT` lines for all
three booking IDs, (c) the `[OUTPUT] booking.summaries -> BKG-30xx` lines for the
two completed bookings, and (d) the final `[CHECKPOINT]` block with its `Status` line.

## Actual Result
**NOT YET TESTED — this Claude session has no Maven/JDK-with-dependencies/internet
access to Maven Central to compile or run this project.** The code above has been
written and manually reviewed for correctness (brace/structure-balanced, uses only
dependencies already declared in `pom.xml`), but it has not been executed. Windows
testing is required before this can be marked PASS or FAIL.

## Status
**IMPLEMENTED — WINDOWS TEST PENDING**
