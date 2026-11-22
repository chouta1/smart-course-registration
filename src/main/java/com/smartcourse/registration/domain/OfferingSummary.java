package com.smartcourse.registration.domain;

public record OfferingSummary(
        String offeringId,
        int capacity,
        long enrolledCount,
        long availableSeats,
        long waitlistedCount) {
}
