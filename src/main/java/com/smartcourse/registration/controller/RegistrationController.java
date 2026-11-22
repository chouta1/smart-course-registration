package com.smartcourse.registration.controller;

import com.smartcourse.registration.dto.DropResponse;
import com.smartcourse.registration.dto.EnrollLearnerRequest;
import com.smartcourse.registration.dto.EnrollmentResponse;
import com.smartcourse.registration.domain.OfferingSummary;
import com.smartcourse.registration.service.RegistrationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/offerings")
public class RegistrationController {

    private final RegistrationService registrationService;

    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @GetMapping("/{offeringId}")
    public OfferingSummary getOfferingSummary(
            @PathVariable String offeringId) {
        return registrationService.getOfferingSummary(offeringId);
    }

    @PostMapping("/{offeringId}/enrollments")
    public EnrollmentResponse enroll(
            @PathVariable String offeringId,
            @Valid @RequestBody EnrollLearnerRequest request) {
        return EnrollmentResponse.from(
                registrationService.enroll(offeringId, request.learnerId()));
    }

    @DeleteMapping("/{offeringId}/enrollments/{learnerId}")
    public DropResponse drop(
            @PathVariable String offeringId,
            @PathVariable String learnerId) {
        return DropResponse.from(registrationService.drop(offeringId, learnerId));
    }
}
