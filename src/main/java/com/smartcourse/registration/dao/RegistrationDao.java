package com.smartcourse.registration.dao;

import com.smartcourse.registration.domain.EnrollmentStatus;
import com.smartcourse.registration.domain.OfferingSummary;
import java.util.List;
import java.util.Optional;

public interface RegistrationDao {

    Optional<OfferingSummary> findOfferingSummary(String offeringId);

    Optional<CourseOfferingEntityProjection> findOfferingForUpdate(String offeringId);

    CourseOfferingEntityProjection createOffering(String offeringId, int capacity);

    Optional<EnrollmentStatus> findStatus(String offeringId, String learnerId);

    long countEnrolled(String offeringId);

    long countWaitlisted(String offeringId);

    List<String> findWaitlistedLearnerIds(String offeringId);

    Optional<Long> findWaitlistPosition(String offeringId, String learnerId);

    void enroll(String offeringId, String learnerId);

    void waitlist(String offeringId, String learnerId);

    boolean promote(String offeringId, String learnerId);

    boolean drop(String offeringId, String learnerId);
}
