# MARSLINE EIP — ITP103 Midterm Lab Exam

**Group:** MARSLINE
**Members (alphabetical by family name):**
1. Baculinao, Mark Joseph L.
2. Bernal, Mars Jairus G.
3. Bunao, Ron Rupert M.
4. Butin, Josel Reimar M.
5. Gana, Nathaniel E.

**Enterprise context:** MARSLINE, a provincial bus line based in Cabuyao,
Laguna (public bus transit, P2P coasters, bus rentals, ticketing, fleet,
inventory, HR, accounting).

## Current Build Status

| Task | Pattern | Status |
|------|---------|--------|
| 1 | Message Channel | ✅ Implemented — Windows test pending |
| 2 | Content-Based Router | ✅ Implemented — Windows test pending |
| 3 | Aggregator | ✅ Implemented — Windows test pending |
| 4 | Message Translator | ✅ Implemented — Windows test pending |
| 5 | Error Channel + Retry | ✅ Implemented — Windows test pending |

All five tasks now have code in this repository (see `docs/checkpoints/`
for each task's checkpoint doc), but **none of it has been compiled or
run yet** — this environment has no Maven and no network access to
Maven Central, so nothing here can be honestly marked PASS until it is
built and run on Windows. The GUI/dashboard and the PowerPoint are
handled in a later build step and are **not** part of this codebase yet.

## Technology

- Java 17
- Maven
- Apache Camel 4.8.0 (uses the Jakarta JMS API — `jakarta.jms.*`)
- Embedded ActiveMQ "Classic" 6.1.8 (`vm://` transport — the broker runs
  inside the same JVM as the application; no separate server, no Docker,
  no install beyond Java + Maven). **Must be the 6.x line**: ActiveMQ
  Classic 5.18.x is built against the older `javax.jms.*` API and is not
  compatible with Camel 4.x's `camel-jms` component, which requires
  `jakarta.jms.ConnectionFactory`.
- Jackson 2.17.2 (JSON)
- slf4j-simple (console logging)

## Project Structure

```
marsline-eip/
├── pom.xml
├── README.md
├── docs/
│   └── checkpoints/
│       ├── task1-message-channel.md
│       ├── task2-content-router.md
│       ├── task3-aggregator.md
│       ├── task4-message-translator.md
│       └── task5-error-retry.md
└── src/
    └── main/
        ├── java/
        │   └── com/marsline/eip/
        │       ├── MarslineEipApplication.java   (entry point / task switch)
        │       ├── broker/
        │       │   └── BrokerConfig.java          (embedded ActiveMQ wiring)
        │       ├── model/
        │       │   └── BookingMessage.java         (shared booking payload, Tasks 1-2)
        │       ├── task1/
        │       │   └── Task1MessageChannel.java
        │       ├── task2/
        │       │   └── Task2ContentBasedRouter.java
        │       ├── task3/
        │       │   ├── BookingPart.java             (BOOKING/PAYMENT/TRIP fragment model)
        │       │   ├── BookingSummary.java           (aggregated result)
        │       │   ├── BookingAggregationStrategy.java (real Camel AggregationStrategy)
        │       │   └── Task3Aggregator.java
        │       ├── task4/
        │       │   ├── LegacyTicket.java             (target JSON shape)
        │       │   └── Task4MessageTranslator.java   (XML -> JSON translator)
        │       └── task5/
        │           └── Task5ErrorChannelRetry.java   (onException retry + parking lot)
        └── resources/
            └── simplelogger.properties
```

## Prerequisites (Windows)

Open **Command Prompt** and check both tools are installed:

```cmd
java -version
mvn -version
```

You need Java 17 or newer, and any recent Maven 3.8+. If either command
is not recognized, install a JDK 17 (e.g. Temurin) and Apache Maven, and
make sure both are on your `PATH`, then reopen CMD.

## Setup

```cmd
cd C:\path\to\marsline-eip
mvn clean compile
```

The first run downloads Camel, ActiveMQ, and Jackson from Maven Central —
this needs an internet connection once. Later runs are offline.

## Running Task 1 — Message Channel

```cmd
mvn compile exec:java -Dexec.args="task1"
```

Sends 3 bookings (BKG-1001, BKG-1002, BKG-1003) from the Online Booking
System through the `marsline.booking.requests` queue to the Booking
Backend, then prints a `[CHECKPOINT]` block confirming all 3 arrived with
their payload intact.

## Running Task 2 — Content-Based Router

```cmd
mvn compile exec:java -Dexec.args="task2"
```

Sends 5 bookings through `marsline.booking.inbound`. The router reads
each booking's `destination` and sends it to `marsline.booking.local`
(Metro Manila destinations) or `marsline.booking.provincial` (everything
else), then prints a `[CHECKPOINT]` block confirming each one landed in
the correct branch.

## Running Task 3 — Aggregator

```cmd
mvn compile exec:java -Dexec.args="task3"
```

Sends BOOKING/PAYMENT/TRIP fragments for 3 bookings through
`booking.parts`, interleaved out of order. Camel's real Aggregator EIP
correlates them by `bookingId`; 2 bookings (BKG-3001, BKG-3002) receive
all 3 parts and are forwarded to `booking.summaries` for Fulfillment,
while 1 booking (BKG-3003) is deliberately left incomplete (its TRIP
part is never sent) to prove an incomplete booking never produces a
false-complete summary. See `docs/checkpoints/task3-aggregator.md`.

## Running Task 4 — Message Translator

```cmd
mvn compile exec:java -Dexec.args="task4"
```

Translates 3 legacy XML ticket records into JSON using a real DOM XML
parse (JDK `DocumentBuilder`) and Jackson serialization, then verifies
all 7 fields per ticket survived the translation. See
`docs/checkpoints/task4-message-translator.md`.

## Running Task 5 — Error Channel + Retry

```cmd
mvn compile exec:java -Dexec.args="task5"
```

Sends 3 bookings to a simulated Booking Backend that genuinely fails a
configured number of times per booking. Camel's real `onException` /
redelivery policy retries each failed booking; 2 recover after retrying
(BKG-5001, BKG-5002), and 1 permanently-failing booking (BKG-5003) is
routed to `marsline.booking.parkinglot` once retries are exhausted. Every
failed attempt is also recorded on the real `marsline.booking.errors`
queue. See `docs/checkpoints/task5-error-retry.md`.

## Rebuilding from clean

```cmd
mvn clean compile
```

## Documentation

Checkpoint write-ups (with blanks for your real screenshots) live in
`docs/checkpoints/`. Fill these in after you personally run each task and
capture your own console output — no fabricated evidence.
