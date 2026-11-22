package com.smartcourse.registration.domain;

public record DropResult(
        String offeringId,
        String droppedLearnerId,
        String promotedLearnerId) {
}
