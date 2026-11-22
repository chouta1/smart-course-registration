package com.smartcourse.registration.domain;

public record EnrollmentResult(
        String offeringId,
        String learnerId,
        EnrollmentStatus status,
        Long waitlistPosition) {
}
