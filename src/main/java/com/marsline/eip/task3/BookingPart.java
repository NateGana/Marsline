package com.marsline.eip.task3;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One fragment of a MARSLINE booking, coming from one of three
 * independent upstream systems:
 *
 *   type = "BOOKING" -> from the Booking System
 *   type = "PAYMENT" -> from the Payment System
 *   type = "TRIP"    -> from the Trip Assignment System
 *
 * All three fragment types share {@code bookingId}, which is the
 * correlation key the Task 3 Aggregator uses to recombine them into a
 * single {@link BookingSummary}. Fields that do not apply to a given
 * type are simply left null (and are excluded from JSON output via
 * {@code @JsonInclude(NON_NULL)}), which is why this class intentionally
 * carries the union of all three schemas rather than one class per type -
 * it keeps the correlation/aggregation code (which only cares about
 * bookingId + type) simple and uniform.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BookingPart {

    @JsonProperty("type")
    private String type;

    @JsonProperty("bookingId")
    private String bookingId;

    // ----- BOOKING fields -----
    @JsonProperty("customerName")
    private String customerName;

    @JsonProperty("origin")
    private String origin;

    @JsonProperty("destination")
    private String destination;

    @JsonProperty("travelDate")
    private String travelDate;

    // ----- PAYMENT fields -----
    @JsonProperty("paymentId")
    private String paymentId;

    @JsonProperty("amount")
    private Double amount;

    @JsonProperty("status")
    private String status;

    // ----- TRIP fields -----
    @JsonProperty("tripId")
    private String tripId;

    @JsonProperty("busNumber")
    private String busNumber;

    @JsonProperty("seatNumber")
    private String seatNumber;

    public BookingPart() {
        // required by Jackson
    }

    public static BookingPart booking(String bookingId, String customerName, String origin,
                                       String destination, String travelDate) {
        BookingPart p = new BookingPart();
        p.type = "BOOKING";
        p.bookingId = bookingId;
        p.customerName = customerName;
        p.origin = origin;
        p.destination = destination;
        p.travelDate = travelDate;
        return p;
    }

    public static BookingPart payment(String bookingId, String paymentId, double amount, String status) {
        BookingPart p = new BookingPart();
        p.type = "PAYMENT";
        p.bookingId = bookingId;
        p.paymentId = paymentId;
        p.amount = amount;
        p.status = status;
        return p;
    }

    public static BookingPart trip(String bookingId, String tripId, String busNumber, String seatNumber) {
        BookingPart p = new BookingPart();
        p.type = "TRIP";
        p.bookingId = bookingId;
        p.tripId = tripId;
        p.busNumber = busNumber;
        p.seatNumber = seatNumber;
        return p;
    }

    public String getType() {
        return type;
    }

    public String getBookingId() {
        return bookingId;
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

    public String getStatus() {
        return status;
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
        return "BookingPart{type='" + type + "', bookingId='" + bookingId + "'}";
    }
}
