package com.smartcourse.registration.service.impl;

import com.smartcourse.registration.dao.CourseOfferingEntityProjection;
import com.smartcourse.registration.dao.RegistrationDao;
import com.smartcourse.registration.domain.DropResult;
import com.smartcourse.registration.domain.EnrollmentResult;
import com.smartcourse.registration.domain.EnrollmentStatus;
import com.smartcourse.registration.domain.IdentifierValidator;
import com.smartcourse.registration.domain.OfferingSummary;
import com.smartcourse.registration.exception.EnrollmentNotFoundException;
import com.smartcourse.registration.exception.OfferingNotFoundException;
import com.smartcourse.registration.exception.PrerequisiteNotMetException;
import com.smartcourse.registration.service.PrerequisiteEligibilityPolicy;
import com.smartcourse.registration.service.RegistrationService;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationServiceImpl implements RegistrationService {

    private final RegistrationDao registrationDao;
    private final PrerequisiteEligibilityPolicy eligibilityPolicy;

    public RegistrationServiceImpl(
            RegistrationDao registrationDao,
            PrerequisiteEligibilityPolicy eligibilityPolicy) {
        this.registrationDao = registrationDao;
        this.eligibilityPolicy = eligibilityPolicy;
    }

    @Override
    @Transactional
    public EnrollmentResult enroll(String offeringId, String learnerId) {
        validateIdentifiers(offeringId, learnerId);
        CourseOfferingEntityProjection offering =
                requireOfferingForUpdate(offeringId);

        Optional<EnrollmentStatus> currentStatus =
                registrationDao.findStatus(offeringId, learnerId);
        if (currentStatus.isPresent()) {
            return toEnrollmentResult(offeringId, learnerId, currentStatus.get());
        }

        if (!eligibilityPolicy.isEligible(learnerId)) {
            throw new PrerequisiteNotMetException(learnerId);
        }

        long enrolledCount = registrationDao.countEnrolled(offeringId);
        List<String> waitlistedLearnerIds =
                registrationDao.findWaitlistedLearnerIds(offeringId);

        if (enrolledCount < offering.capacity() && waitlistedLearnerIds.isEmpty()) {
            registrationDao.enroll(offeringId, learnerId);
        } else {
            registrationDao.waitlist(offeringId, learnerId);
            while (registrationDao.countEnrolled(offeringId) < offering.capacity()) {
                if (promoteNextEligibleLearner(offeringId).isEmpty()) {
                    break;
                }
            }
        }

        EnrollmentStatus resultingStatus = registrationDao
                .findStatus(offeringId, learnerId)
                .orElseThrow(() -> new IllegalStateException(
                        "Enrollment state was not persisted for the requested learner."));
        return toEnrollmentResult(offeringId, learnerId, resultingStatus);
    }

    @Override
    @Transactional
    public DropResult drop(String offeringId, String learnerId) {
        validateIdentifiers(offeringId, learnerId);
        requireOfferingForUpdate(offeringId);

        if (!registrationDao.drop(offeringId, learnerId)) {
            throw new EnrollmentNotFoundException(learnerId);
        }

        return new DropResult(
                offeringId,
                learnerId,
                promoteNextEligibleLearner(offeringId).orElse(null));
    }

    @Override
    @Transactional(readOnly = true)
    public OfferingSummary getOfferingSummary(String offeringId) {
        IdentifierValidator.requireValid(offeringId, "offeringId");
        return registrationDao.findOfferingSummary(offeringId)
                .orElseThrow(() -> new OfferingNotFoundException(offeringId));
    }

    private CourseOfferingEntityProjection requireOfferingForUpdate(String offeringId) {
        return registrationDao.findOfferingForUpdate(offeringId)
                .orElseThrow(() -> new OfferingNotFoundException(offeringId));
    }

    private Optional<String> promoteNextEligibleLearner(String offeringId) {
        for (String learnerId : registrationDao.findWaitlistedLearnerIds(offeringId)) {
            if (eligibilityPolicy.isEligible(learnerId)
                    && registrationDao.promote(offeringId, learnerId)) {
                return Optional.of(learnerId);
            }
        }
        return Optional.empty();
    }

    private EnrollmentResult toEnrollmentResult(
            String offeringId,
            String learnerId,
            EnrollmentStatus status) {
        Optional<Long> position = status == EnrollmentStatus.WAITLISTED
                ? registrationDao.findWaitlistPosition(offeringId, learnerId)
                : Optional.empty();
        return new EnrollmentResult(
                offeringId,
                learnerId,
                status,
                position.orElse(null));
    }

    private static void validateIdentifiers(String offeringId, String learnerId) {
        IdentifierValidator.requireValid(offeringId, "offeringId");
        IdentifierValidator.requireValid(learnerId, "learnerId");
    }
}
