package com.smartcourse.registration.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartcourse.registration.domain.DropResult;
import com.smartcourse.registration.domain.EnrollmentResult;
import com.smartcourse.registration.domain.EnrollmentStatus;
import com.smartcourse.registration.domain.OfferingSummary;
import com.smartcourse.registration.dto.DropResponse;
import com.smartcourse.registration.dto.EnrollLearnerRequest;
import com.smartcourse.registration.dto.EnrollmentResponse;
import com.smartcourse.registration.service.RegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RegistrationControllerTest {

    private RegistrationService registrationService;
    private RegistrationController controller;

    @BeforeEach
    void setUp() {
        registrationService = org.mockito.Mockito.mock(RegistrationService.class);
        controller = new RegistrationController(registrationService);
    }

    @Test
    void returnsOfferingSummaryFromService() {
        OfferingSummary summary = new OfferingSummary("course-101", 2, 1, 1, 3);
        when(registrationService.getOfferingSummary("course-101")).thenReturn(summary);

        assertEquals(summary, controller.getOfferingSummary("course-101"));
        verify(registrationService).getOfferingSummary("course-101");
    }

    @Test
    void mapsEnrollmentResultToResponse() {
        when(registrationService.enroll("course-101", "learner-1"))
                .thenReturn(new EnrollmentResult(
                        "course-101",
                        "learner-1",
                        EnrollmentStatus.WAITLISTED,
                        1L));

        EnrollmentResponse response = controller.enroll(
                "course-101",
                new EnrollLearnerRequest("learner-1"));

        assertEquals("course-101", response.offeringId());
        assertEquals("learner-1", response.learnerId());
        assertEquals(EnrollmentStatus.WAITLISTED, response.status());
        assertEquals(1L, response.waitlistPosition());
        verify(registrationService).enroll("course-101", "learner-1");
    }

    @Test
    void mapsDropResultToResponse() {
        when(registrationService.drop("course-101", "learner-1"))
                .thenReturn(new DropResult("course-101", "learner-1", "learner-2"));

        DropResponse response = controller.drop("course-101", "learner-1");

        assertEquals("course-101", response.offeringId());
        assertEquals("learner-1", response.droppedLearnerId());
        assertEquals("learner-2", response.promotedLearnerId());
        assertEquals("DROPPED", response.status());
        verify(registrationService).drop("course-101", "learner-1");
    }
}
