# Task 1 Checkpoint — Message Channel

**Group:** MARSLINE
**Members (alphabetical):** Baculinao, Mark Joseph L. · Bernal, Mars Jairus G. · Bunao, Ron Rupert M. · Butin, Josel Reimar M. · Gana, Nathaniel E.

## Objective
Demonstrate a working Message Channel: a booking created by the MARSLINE
Online Booking System travels through a JMS queue and is received intact
by the MARSLINE Booking Backend.

## Scenario
```
MARSLINE Online Booking System
        |
   JSON booking message
        |
        v
marsline.booking.requests   (JMS queue, embedded ActiveMQ broker)
        |
        v
MARSLINE Booking Backend
```

## Architecture / Queue
- Pattern: Message Channel (point-to-point)
- Queue name: `marsline.booking.requests`
- Broker: embedded ActiveMQ Classic 5.18.3 (`vm://marsline-broker`, in-process, non-persistent)
- Route class: `com.marsline.eip.task1.Task1MessageChannel`

## Test Input
| Booking ID | Customer        | Origin  | Destination | Travel Date | Seat |
|------------|-----------------|---------|-------------|-------------|------|
| BKG-1001   | Juan Dela Cruz  | Cabuyao | Manila      | 2026-09-15  | 12A  |
| BKG-1002   | Maria Santos    | Cabuyao | Batangas    | 2026-09-15  | 07C  |
| BKG-1003   | Pedro Reyes     | Cabuyao | Quezon      | 2026-09-16  | 03B  |

## Expected Result
All 3 booking messages are sent to `marsline.booking.requests` and are
received by the Booking Backend with the exact same JSON payload that was
sent (no data loss), each carrying a real broker-assigned JMSMessageID.

## Actual Result
_(Fill in after running `mvn compile exec:java -Dexec.args="task1"` on your machine.)_

- Messages sent:
- Messages received:
- Payloads intact (Y/N):
- Console/checkpoint block observed: PASS / FAIL

## Checkpoint
- [ ] PASS
- [ ] FAIL

## Screenshot Evidence
Capture and insert here:
1. Full console output from startup to the `[CHECKPOINT]` block.
2. Close-up of the `[CHECKPOINT]` block showing "Status: PASS".

_Do not fabricate screenshots — insert the real console capture from your run._

## Explanation
The message never travels directly from the source to the target class —
it is serialized to JSON, handed to the embedded broker via the Camel JMS
producer, stored/dispatched by ActiveMQ, and independently consumed by a
separate Camel route (`task1-target-booking-backend`). This is what makes
it a genuine Message Channel rather than a simulated method call.
