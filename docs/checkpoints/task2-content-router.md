# Task 2 Checkpoint — Content-Based Router

**Group:** MARSLINE
**Members (alphabetical):** Baculinao, Mark Joseph L. · Bernal, Mars Jairus G. · Bunao, Ron Rupert M. · Butin, Josel Reimar M. · Gana, Nathaniel E.

## Objective
Demonstrate a working Content-Based Router: incoming bookings are routed
to different processing systems based on the actual `destination` field
of each message.

## Routing Rule
A booking is classified **LOCAL** only if its `destination` (case
insensitive) is one of these Metro Manila cities:
`Manila, Makati, Quezon City, Pasig, Taguig, Mandaluyong, Pasay, Paranaque, Caloocan`

Every other destination is classified **PROVINCIAL**.

> Note: "Quezon" (the province) and "Quezon City" (a Metro Manila city)
> are intentionally different destinations in this project — a booking to
> "Quezon" is PROVINCIAL, a booking to "Quezon City" would be LOCAL.

Implemented in: `Task2ContentBasedRouter.classify(String destination)`

## Architecture
```
Booking Intake
      |
      v
marsline.booking.inbound
      |
      v
Content-Based Router (reads "destination")
      |
      +---- LOCAL ------> marsline.booking.local ------> Local/Metro Processing System
      |
      +---- PROVINCIAL --> marsline.booking.provincial --> Provincial Processing System
```

- Queues: `marsline.booking.inbound`, `marsline.booking.local`, `marsline.booking.provincial`
- Broker: embedded ActiveMQ Classic 5.18.3 (`vm://marsline-broker`, shared config with Task 1)
- Route class: `com.marsline.eip.task2.Task2ContentBasedRouter`

## Test Input
| Booking ID | Destination | Expected Branch |
|------------|-------------|------------------|
| BKG-2001   | Manila      | LOCAL            |
| BKG-2002   | Makati      | LOCAL            |
| BKG-2003   | Batangas    | PROVINCIAL       |
| BKG-2004   | Quezon      | PROVINCIAL       |
| BKG-2005   | Pampanga    | PROVINCIAL       |

## Expected Result
All 5 bookings are routed to the branch matching the rule above:
2 LOCAL, 3 PROVINCIAL, 5/5 correct.

## Actual Result
_(Fill in after running `mvn compile exec:java -Dexec.args="task2"` on your machine.)_

- Local received:
- Provincial received:
- Correct routing count:
- Console/checkpoint block observed: PASS / FAIL

## Checkpoint
- [ ] PASS
- [ ] FAIL

## Screenshot Evidence
Capture and insert here:
1. Console output showing each `[ROUTER]` decision as messages are routed.
2. The final `[CHECKPOINT]` block showing per-message detail and "Status: PASS".

_Do not fabricate screenshots — insert the real console capture from your run._

## Explanation
The routing decision is computed inside a Camel `.choice()/.when()/.otherwise()`
block from the actual `destination` field parsed out of each JSON message —
not hard-coded to always choose one branch. Each branch is a separate real
JMS queue consumed by a separate Camel route, so both LOCAL and PROVINCIAL
paths are genuinely exercised, not merely printed.
