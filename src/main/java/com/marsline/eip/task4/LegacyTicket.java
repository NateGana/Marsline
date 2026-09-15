package com.marsline.eip.task4;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The modern JSON representation of a MARSLINE legacy ticket record,
 * produced by {@link Task4MessageTranslator} out of the legacy XML
 * ticket format. Field names intentionally mirror the legacy XML tag
 * names one-for-one so field preservation can be checked directly.
 */
public class LegacyTicket {

    @JsonProperty("ticketId")
    private String ticketId;

    @JsonProperty("bookingId")
    private String bookingId;

    @JsonProperty("customerName")
    private String customerName;

    @JsonProperty("origin")
    private String origin;

    @JsonProperty("destination")
    private String destination;

    @JsonProperty("travelDate")
    private String travelDate;

    @JsonProperty("seatNumber")
    private String seatNumber;

    public String getTicketId() {
        return ticketId;
    }

    public void setTicketId(String ticketId) {
        this.ticketId = ticketId;
    }

    public String getBookingId() {
        return bookingId;
    }

    public void setBookingId(String bookingId) {
        this.bookingId = bookingId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public String getTravelDate() {
        return travelDate;
    }

    public void setTravelDate(String travelDate) {
        this.travelDate = travelDate;
    }

    public String getSeatNumber() {
        return seatNumber;
    }

    public void setSeatNumber(String seatNumber) {
        this.seatNumber = seatNumber;
    }

    /** Field-name -> value view, used purely to count/compare preserved fields for the checkpoint. */
    public Map<String, String> asFieldMap() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("ticketId", ticketId);
        m.put("bookingId", bookingId);
        m.put("customerName", customerName);
        m.put("origin", origin);
        m.put("destination", destination);
        m.put("travelDate", travelDate);
        m.put("seatNumber", seatNumber);
        return m;
    }
}
