package com.smartcourse.registration.dto;

import com.smartcourse.registration.domain.EnrollmentResult;
import com.smartcourse.registration.domain.EnrollmentStatus;

public record EnrollmentResponse(
        String offeringId,
        String learnerId,
        EnrollmentStatus status,
        Long waitlistPosition) {

    public static EnrollmentResponse from(EnrollmentResult result) {
        return new EnrollmentResponse(
                result.offeringId(),
                result.learnerId(),
                result.status(),
                result.waitlistPosition());
    }
}
