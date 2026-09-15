package com.marsline.eip.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * MARSLINE booking payload.
 *
 * This is the message that travels through every JMS queue in this
 * project (Task 1 Message Channel, Task 2 Content-Based Router, and
 * later tasks). It represents a single passenger booking coming from
 * the MARSLINE Online Booking System.
 */
public class BookingMessage {

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

    public BookingMessage() {
        // required by Jackson
    }

    public BookingMessage(String bookingId, String customerName, String origin,
                           String destination, String travelDate, String seatNumber) {
        this.bookingId = bookingId;
        this.customerName = customerName;
        this.origin = origin;
        this.destination = destination;
        this.travelDate = travelDate;
        this.seatNumber = seatNumber;
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

    @Override
    public String toString() {
        return "BookingMessage{" +
                "bookingId='" + bookingId + '\'' +
                ", customerName='" + customerName + '\'' +
                ", origin='" + origin + '\'' +
                ", destination='" + destination + '\'' +
                ", travelDate='" + travelDate + '\'' +
                ", seatNumber='" + seatNumber + '\'' +
                '}';
    }
}
