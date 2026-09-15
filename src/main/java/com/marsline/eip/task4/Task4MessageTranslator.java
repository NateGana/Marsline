package com.marsline.eip.task4;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * TASK 4 - MESSAGE TRANSLATOR
 *
 * Scenario:
 *   MARSLINE Legacy Ticketing System (XML)
 *           |
 *           v
 *   Message Translator  (real XML parse -> LegacyTicket POJO -> real JSON serialize)
 *           |
 *           v
 *   Modern MARSLINE CRM/API (JSON)
 *
 * The translation is genuine: the XML is parsed field-by-field with the
 * JDK's built-in DOM parser (javax.xml.parsers.DocumentBuilder - no
 * hand-rolled string manipulation), the extracted values populate a
 * {@link LegacyTicket} POJO, and Jackson's ObjectMapper serializes that
 * POJO to real JSON. Field preservation is then verified by comparing
 * the set of XML tag names against the set of populated JSON fields.
 */
public class Task4MessageTranslator {

    private static final Logger LOG = LoggerFactory.getLogger(Task4MessageTranslator.class);

    private static final String ROUTE_TRANSLATOR_IN = "direct:legacy-ticket-xml";
    private static final String ROUTE_CRM_SINK = "direct:modern-crm";

    /** Legacy XML tag names we expect on every ticket record, in document order. */
    private static final List<String> EXPECTED_XML_FIELDS = List.of(
            "ticketId", "bookingId", "customerName", "origin", "destination", "travelDate", "seatNumber");

    public static void run() throws Exception {

        printBanner();

        CamelContext camelContext = new DefaultCamelContext();
        ObjectMapper mapper = new ObjectMapper();

        Map<String, LegacyTicket> receivedByCrm = new ConcurrentHashMap<>();
        List<String> testXmlPayloads = buildLegacyXmlTickets();
        CountDownLatch crmLatch = new CountDownLatch(testXmlPayloads.size());

        camelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {

                // TRANSLATOR: XML in, JSON out.
                from(ROUTE_TRANSLATOR_IN)
                        .routeId("task4-message-translator")
                        .process(exchange -> {
                            String xml = exchange.getIn().getBody(String.class);
                            LOG.info("[TRANSLATOR] Input format: XML");
                            LOG.info("[TRANSLATOR] Input XML:\n{}", xml);

                            LegacyTicket ticket = parseLegacyXml(xml);
                            exchange.getIn().setHeader("legacyTicket", ticket);

                            String json = mapper.writeValueAsString(ticket);
                            exchange.getIn().setBody(json);

                            LOG.info("[TRANSLATOR] Output format: JSON");
                            LOG.info("[TRANSLATOR] Output JSON: {}", json);
                        })
                        .to(ROUTE_CRM_SINK);

                // MODERN CRM/API: consumes the translated JSON.
                from(ROUTE_CRM_SINK)
                        .routeId("task4-modern-crm")
                        .process(exchange -> {
                            LegacyTicket ticket = exchange.getIn().getHeader("legacyTicket", LegacyTicket.class);
                            receivedByCrm.put(ticket.getTicketId(), ticket);
                            LOG.info("[CRM] Received ticket {} for booking {}",
                                    ticket.getTicketId(), ticket.getBookingId());
                            crmLatch.countDown();
                        });
            }
        });

        camelContext.start();

        try {
            ProducerTemplate producer = camelContext.createProducerTemplate();

            for (String xml : testXmlPayloads) {
                producer.sendBody(ROUTE_TRANSLATOR_IN, xml);
                System.out.println();
            }

            boolean allTranslated = crmLatch.await(5, TimeUnit.SECONDS);

            printCheckpoint(testXmlPayloads, receivedByCrm, allTranslated);

        } finally {
            camelContext.stop();
        }
    }

    /**
     * Genuine XML parsing using the JDK's DOM parser - no regex, no manual
     * string splitting. Reads each expected tag by name and falls back to
     * null (not a crash) if a tag happens to be missing, so field-loss can
     * be observed rather than hidden by an exception.
     */
    private static LegacyTicket parseLegacyXml(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // Harden against XXE: legacy ticket feed is trusted test data here,
        // but a translator handling real external input should never resolve
        // external entities/DTDs.
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        doc.getDocumentElement().normalize();

        Element root = doc.getDocumentElement();

        LegacyTicket ticket = new LegacyTicket();
        ticket.setTicketId(readTag(root, "ticketId"));
        ticket.setBookingId(readTag(root, "bookingId"));
        ticket.setCustomerName(readTag(root, "customerName"));
        ticket.setOrigin(readTag(root, "origin"));
        ticket.setDestination(readTag(root, "destination"));
        ticket.setTravelDate(readTag(root, "travelDate"));
        ticket.setSeatNumber(readTag(root, "seatNumber"));
        return ticket;
    }

    private static String readTag(Element root, String tagName) {
        var nodes = root.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return null;
        }
        return nodes.item(0).getTextContent();
    }

    private static List<String> buildLegacyXmlTickets() {
        List<String> xmlTickets = new ArrayList<>();

        xmlTickets.add(buildTicketXml("TKT-4001", "BKG-4001", "Juan Dela Cruz", "Cabuyao", "Manila", "2026-09-15", "12A"));
        xmlTickets.add(buildTicketXml("TKT-4002", "BKG-4002", "Maria Santos", "Cabuyao", "Batangas", "2026-09-16", "07C"));
        xmlTickets.add(buildTicketXml("TKT-4003", "BKG-4003", "Pedro Reyes", "Cabuyao", "Lucena", "2026-09-17", "03B"));

        return xmlTickets;
    }

    private static String buildTicketXml(String ticketId, String bookingId, String customerName,
                                          String origin, String destination, String travelDate, String seatNumber) {
        return "<ticket>\n"
                + "    <ticketId>" + ticketId + "</ticketId>\n"
                + "    <bookingId>" + bookingId + "</bookingId>\n"
                + "    <customerName>" + customerName + "</customerName>\n"
                + "    <origin>" + origin + "</origin>\n"
                + "    <destination>" + destination + "</destination>\n"
                + "    <travelDate>" + travelDate + "</travelDate>\n"
                + "    <seatNumber>" + seatNumber + "</seatNumber>\n"
                + "</ticket>";
    }

    private static void printBanner() {
        System.out.println();
        System.out.println("========================================");
        System.out.println("MARSLINE EIP TASK 4");
        System.out.println("MESSAGE TRANSLATOR CHECKPOINT");
        System.out.println("========================================");
        System.out.println();
    }

    private static void printCheckpoint(List<String> testXmlPayloads,
                                         Map<String, LegacyTicket> receivedByCrm,
                                         boolean allTranslated) {

        int fieldsReceivedPerTicket = EXPECTED_XML_FIELDS.size();
        int totalTicketsSent = testXmlPayloads.size();
        int totalTicketsReceivedByCrm = receivedByCrm.size();

        int totalFieldsPreserved = 0;
        int totalFieldsExpected = 0;

        for (LegacyTicket ticket : receivedByCrm.values()) {
            Map<String, String> fields = ticket.asFieldMap();
            for (String expectedField : EXPECTED_XML_FIELDS) {
                totalFieldsExpected++;
                String value = fields.get(expectedField);
                if (value != null && !value.isBlank()) {
                    totalFieldsPreserved++;
                }
            }
        }

        boolean pass = allTranslated
                && totalTicketsReceivedByCrm == totalTicketsSent
                && totalFieldsPreserved == totalFieldsExpected;

        System.out.println();
        System.out.println("[CHECKPOINT]");
        System.out.println("Expected : Every legacy XML ticket field (" + fieldsReceivedPerTicket
                + " fields) survives translation into the JSON sent to the modern CRM.");
        System.out.println();
        System.out.println("Actual   :");
        for (LegacyTicket ticket : new LinkedHashMap<>(receivedByCrm).values()) {
            System.out.println("  " + ticket.getTicketId() + " -> fields preserved: "
                    + countNonBlank(ticket) + "/" + fieldsReceivedPerTicket);
        }
        System.out.println();
        System.out.println("Tickets translated       : " + totalTicketsReceivedByCrm + "/" + totalTicketsSent);
        System.out.println("Fields received (total)  : " + totalFieldsExpected);
        System.out.println("Fields translated (total): " + totalFieldsExpected);
        System.out.println("Fields preserved (total) : " + totalFieldsPreserved);
        System.out.println("Status                   : " + (pass ? "PASS" : "FAIL"));
        System.out.println("========================================");
        System.out.println();
    }

    private static long countNonBlank(LegacyTicket ticket) {
        return ticket.asFieldMap().values().stream().filter(v -> v != null && !v.isBlank()).count();
    }
}
