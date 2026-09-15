package com.marsline.eip.task3;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The combined result the Task 3 Aggregator produces once it has seen
 * the BOOKING, PAYMENT, and TRIP fragments for a given bookingId.
 *
 * This is the object actually accumulated inside the Camel exchange body
 * across multiple {@code aggregate()} calls (see
 * {@link BookingAggregationStrategy}), and it is what gets serialized to
 * JSON and sent to {@code booking.summaries} once (and only once) it is
 * complete.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BookingSummary {

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

    @JsonProperty("paymentId")
    private String paymentId;

    @JsonProperty("amount")
    private Double amount;

    @JsonProperty("paymentStatus")
    private String paymentStatus;

    @JsonProperty("tripId")
    private String tripId;

    @JsonProperty("busNumber")
    private String busNumber;

    @JsonProperty("seatNumber")
    private String seatNumber;

    /** Which part types ("BOOKING", "PAYMENT", "TRIP") have been merged in so far. */
    @JsonProperty("partsReceived")
    private final Set<String> partsReceived = new LinkedHashSet<>();

    /** Required by Jackson for deserializing a booking.summaries JSON message back into an object. */
    public BookingSummary() {
    }

    public BookingSummary(String bookingId) {
        this.bookingId = bookingId;
    }

    /** Used by Jackson when deserializing; restores which parts had already been merged. */
    public void setPartsReceived(List<String> parts) {
        this.partsReceived.clear();
        if (parts != null) {
            this.partsReceived.addAll(parts);
        }
    }

    public void setBookingId(String bookingId) {
        this.bookingId = bookingId;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public void setTravelDate(String travelDate) {
        this.travelDate = travelDate;
    }

    public void setPaymentId(String paymentId) {
        this.paymentId = paymentId;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public void setPaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public void setTripId(String tripId) {
        this.tripId = tripId;
    }

    public void setBusNumber(String busNumber) {
        this.busNumber = busNumber;
    }

    public void setSeatNumber(String seatNumber) {
        this.seatNumber = seatNumber;
    }

    /** Merges one incoming fragment into this summary. Returns this, for chaining. */
    public BookingSummary merge(BookingPart part) {
        partsReceived.add(part.getType());

        switch (part.getType()) {
            case "BOOKING" -> {
                this.customerName = part.getCustomerName();
                this.origin = part.getOrigin();
                this.destination = part.getDestination();
                this.travelDate = part.getTravelDate();
            }
            case "PAYMENT" -> {
                this.paymentId = part.getPaymentId();
                this.amount = part.getAmount();
                this.paymentStatus = part.getStatus();
            }
            case "TRIP" -> {
                this.tripId = part.getTripId();
                this.busNumber = part.getBusNumber();
                this.seatNumber = part.getSeatNumber();
            }
            default -> throw new IllegalArgumentException("Unknown booking part type: " + part.getType());
        }
        return this;
    }

    public String getBookingId() {
        return bookingId;
    }

    public Set<String> getPartsReceived() {
        return partsReceived;
    }

    /** A booking summary is complete only once all three required part types have arrived. */
    public boolean isComplete() {
        return partsReceived.contains("BOOKING")
                && partsReceived.contains("PAYMENT")
                && partsReceived.contains("TRIP");
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getOrigin() {
        return origin;
    }

    public String getDestination() {
        return destination;
    }

    public String getTravelDate() {
        return travelDate;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public Double getAmount() {
        return amount;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public String getTripId() {
        return tripId;
    }

    public String getBusNumber() {
        return busNumber;
    }

    public String getSeatNumber() {
        return seatNumber;
    }

    @Override
    public String toString() {
        return "BookingSummary{bookingId='" + bookingId + "', partsReceived=" + partsReceived
                + ", complete=" + isComplete() + '}';
    }
}
