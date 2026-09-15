package com.marsline.eip.task3;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Real Camel Aggregator EIP strategy (org.apache.camel.AggregationStrategy).
 *
 * Camel calls {@link #aggregate(Exchange, Exchange)} once per incoming
 * exchange that shares the same correlation key (bookingId, set on the
 * route via {@code .aggregate(header("bookingId"), strategy)}).
 *
 *   - oldExchange == null  -> this is the FIRST fragment seen for this
 *     bookingId; a new {@link BookingSummary} is created and returned.
 *   - oldExchange != null  -> a later fragment for the SAME bookingId
 *     arrived; it is merged into the BookingSummary already carried by
 *     oldExchange, and oldExchange is returned (with the updated body).
 *
 * Camel keeps a separate accumulated exchange per distinct correlation
 * key, so fragments for different bookingIds are never mixed together -
 * this is what guarantees BKG-3001's parts never leak into BKG-3002's
 * summary even when messages arrive interleaved/out of order.
 */
public class BookingAggregationStrategy implements AggregationStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(BookingAggregationStrategy.class);

    @Override
    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
        BookingPart incomingPart = newExchange.getIn().getBody(BookingPart.class);

        if (oldExchange == null) {
            // First fragment for this bookingId - start a new summary.
            BookingSummary summary = new BookingSummary(incomingPart.getBookingId()).merge(incomingPart);
            LOG.info("[AGGREGATOR] Received {} for {} (1st part, {}/3)",
                    incomingPart.getType(), incomingPart.getBookingId(), summary.getPartsReceived().size());
            newExchange.getIn().setBody(summary);
            return newExchange;
        }

        BookingSummary summary = oldExchange.getIn().getBody(BookingSummary.class);
        summary.merge(incomingPart);
        LOG.info("[AGGREGATOR] Received {} for {} ({}/3)",
                incomingPart.getType(), incomingPart.getBookingId(), summary.getPartsReceived().size());
        oldExchange.getIn().setBody(summary);
        return oldExchange;
    }
}
