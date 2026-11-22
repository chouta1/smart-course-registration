package com.smartcourse.registration.dto;

import com.smartcourse.registration.domain.DropResult;

public record DropResponse(
        String offeringId,
        String droppedLearnerId,
        String promotedLearnerId,
        String status) {

    public static DropResponse from(DropResult result) {
        return new DropResponse(
                result.offeringId(),
                result.droppedLearnerId(),
                result.promotedLearnerId(),
                "DROPPED");
    }
}
