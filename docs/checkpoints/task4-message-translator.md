# Task 4 Checkpoint — Message Translator

**Group:** MARSLINE
**Members (alphabetical):** Baculinao, Mark Joseph L. · Bernal, Mars Jairus G. · Bunao, Ron Rupert M. · Butin, Josel Reimar M. · Gana, Nathaniel E.

## Objective
Demonstrate a working Message Translator: a legacy MARSLINE ticket record
expressed as XML is genuinely parsed and converted into a JSON
representation suitable for a modern CRM/API, with no data loss.

## MARSLINE Scenario
MARSLINE's original ticketing terminal software only exports ticket
records as XML. The modern CRM/API the company is integrating with only
accepts JSON. A Message Translator sits between the two so neither
system needs to change its native format.

## Architecture
```
Legacy Ticketing System (XML)
            |
            v
   Message Translator
 (javax.xml.parsers.DocumentBuilder parse
   -> LegacyTicket POJO
   -> Jackson ObjectMapper serialize)
            |
            v
   Modern MARSLINE CRM/API (JSON)
```

## EIP Being Demonstrated
**Message Translator** — implemented with a real DOM XML parse
(`javax.xml.parsers.DocumentBuilder`, part of the JDK) feeding a real
Jackson `ObjectMapper.writeValueAsString(...)` JSON serialization. No
regex or manual string substitution is used to "fake" the conversion.

## Components / Classes Used
- `com.marsline.eip.task4.LegacyTicket` — target JSON shape (mirrors the legacy XML tag names)
- `com.marsline.eip.task4.Task4MessageTranslator` — Camel route (`direct:legacy-ticket-xml` → translator processor → `direct:modern-crm`) that performs and verifies the translation

## XML Input (sample)
```xml
<ticket>
    <ticketId>TKT-4001</ticketId>
    <bookingId>BKG-4001</bookingId>
    <customerName>Juan Dela Cruz</customerName>
    <origin>Cabuyao</origin>
    <destination>Manila</destination>
    <travelDate>2026-09-15</travelDate>
    <seatNumber>12A</seatNumber>
</ticket>
```
Three such records are used as test data (TKT-4001/BKG-4001,
TKT-4002/BKG-4002, TKT-4003/BKG-4003).

## Translation Logic
1. The XML string is parsed with `DocumentBuilderFactory` /
   `DocumentBuilder` (DTD/external-entity resolution disabled for
   safety) into a DOM `Document`.
2. Each of the 7 expected tags (`ticketId`, `bookingId`, `customerName`,
   `origin`, `destination`, `travelDate`, `seatNumber`) is read from the
   DOM by tag name and copied onto a `LegacyTicket` POJO field of the
   same name.
3. `ObjectMapper.writeValueAsString(ticket)` produces the JSON actually
   sent onward to the "Modern CRM" route.

## JSON Output (expected, for the sample above)
```json
{"ticketId":"TKT-4001","bookingId":"BKG-4001","customerName":"Juan Dela Cruz","origin":"Cabuyao","destination":"Manila","travelDate":"2026-09-15","seatNumber":"12A"}
```

## Field Preservation Check
The checkpoint compares the 7 expected XML tag names against the 7
corresponding non-blank fields on the JSON-deserialized `LegacyTicket`
the "CRM" route actually received, per ticket, and totals them.

| XML field | JSON field |
|---|---|
| ticketId | ticketId |
| bookingId | bookingId |
| customerName | customerName |
| origin | origin |
| destination | destination |
| travelDate | travelDate |
| seatNumber | seatNumber |

## Expected Result
- 3 tickets translated.
- 7 fields received / 7 fields translated / 7 fields preserved, per ticket.
- 21 fields preserved out of 21 expected, in total.

## Screenshot Instructions
Capture the full console output of:
```
mvn compile exec:java -Dexec.args="task4"
```
showing, per ticket, the `[TRANSLATOR] Input XML`, `[TRANSLATOR] Output JSON`,
and `[CRM] Received ticket ...` lines, plus the final `[CHECKPOINT]` block
with its field-count totals and `Status` line.

## Actual Result
**NOT YET TESTED — this Claude session has no Maven/JDK-with-dependencies/internet
access to Maven Central to compile or run this project.** The translator code has
been written and manually reviewed (uses only the JDK's built-in XML parser plus
the project's existing Jackson dependency — no new dependency added), but it has
not been executed. Windows testing is required before this can be marked PASS or
FAIL.

## Status
**IMPLEMENTED — WINDOWS TEST PENDING**
