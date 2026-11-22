package com.smartcourse.registration.service;

import com.smartcourse.registration.domain.DropResult;
import com.smartcourse.registration.domain.EnrollmentResult;
import com.smartcourse.registration.domain.OfferingSummary;

public interface RegistrationService {

    EnrollmentResult enroll(String offeringId, String learnerId);

    DropResult drop(String offeringId, String learnerId);

    OfferingSummary getOfferingSummary(String offeringId);
}
