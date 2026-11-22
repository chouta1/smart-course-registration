package com.smartcourse.registration.dao;

import com.smartcourse.registration.domain.EnrollmentStatus;
import com.smartcourse.registration.domain.OfferingSummary;
import com.smartcourse.registration.entity.CourseOffering;
import com.smartcourse.registration.entity.CourseRegistration;
import com.smartcourse.registration.entity.RegistrationId;
import com.smartcourse.registration.repository.CourseOfferingRepository;
import com.smartcourse.registration.repository.CourseRegistrationRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class JpaRegistrationDao implements RegistrationDao {

    private final CourseOfferingRepository offeringRepository;
    private final CourseRegistrationRepository registrationRepository;

    public JpaRegistrationDao(
            CourseOfferingRepository offeringRepository,
            CourseRegistrationRepository registrationRepository) {
        this.offeringRepository = offeringRepository;
        this.registrationRepository = registrationRepository;
    }

    @Override
    public Optional<OfferingSummary> findOfferingSummary(String offeringId) {
        return offeringRepository.findById(offeringId).map(offering ->
                new OfferingSummary(
                        offeringId,
                        offering.getCapacity(),
                        countEnrolled(offeringId),
                        Math.max(0, offering.getCapacity() - countEnrolled(offeringId)),
                        countWaitlisted(offeringId)));
    }

    @Override
    public Optional<CourseOfferingEntityProjection> findOfferingForUpdate(String offeringId) {
        return offeringRepository.findByIdForUpdate(offeringId)
                .map(offering -> new CourseOfferingEntityProjection(
                        offering.getOfferingId(),
                        offering.getCapacity()));
    }

    @Override
    public CourseOfferingEntityProjection createOffering(String offeringId, int capacity) {
        CourseOffering saved = offeringRepository.save(
                new CourseOffering(offeringId, capacity));
        return new CourseOfferingEntityProjection(saved.getOfferingId(), saved.getCapacity());
    }

    @Override
    public Optional<EnrollmentStatus> findStatus(String offeringId, String learnerId) {
        return registrationRepository.findById(new RegistrationId(offeringId, learnerId))
                .map(CourseRegistration::getStatus);
    }

    @Override
    public long countEnrolled(String offeringId) {
        return registrationRepository.countByIdOfferingIdAndStatus(
                offeringId,
                EnrollmentStatus.ENROLLED);
    }

    @Override
    public long countWaitlisted(String offeringId) {
        return registrationRepository.countByIdOfferingIdAndStatus(
                offeringId,
                EnrollmentStatus.WAITLISTED);
    }

    @Override
    public List<String> findWaitlistedLearnerIds(String offeringId) {
        return registrationRepository
                .findByIdOfferingIdAndStatusOrderByQueueSequenceAsc(
                        offeringId,
                        EnrollmentStatus.WAITLISTED)
                .stream()
                .map(registration -> registration.getId().getLearnerId())
                .toList();
    }

    @Override
    public Optional<Long> findWaitlistPosition(String offeringId, String learnerId) {
        if (!findStatus(offeringId, learnerId).filter(
                status -> status == EnrollmentStatus.WAITLISTED).isPresent()) {
            return Optional.empty();
        }
        long position = registrationRepository.countWaitlistPosition(offeringId, learnerId);
        return position > 0 ? Optional.of(position) : Optional.empty();
    }

    @Override
    public void enroll(String offeringId, String learnerId) {
        registrationRepository.save(CourseRegistration.enrolled(offeringId, learnerId));
    }

    @Override
    public void waitlist(String offeringId, String learnerId) {
        long sequence = registrationRepository.findMaxWaitlistSequence(offeringId) + 1;
        registrationRepository.save(
                CourseRegistration.waitlisted(offeringId, learnerId, sequence));
    }

    @Override
    public boolean promote(String offeringId, String learnerId) {
        return registrationRepository.findById(
                        new RegistrationId(offeringId, learnerId))
                .filter(registration ->
                        registration.getStatus() == EnrollmentStatus.WAITLISTED)
                .map(registration -> {
                    registration.promote();
                    registrationRepository.save(registration);
                    return true;
                })
                .orElse(false);
    }

    @Override
    public boolean drop(String offeringId, String learnerId) {
        Optional<CourseRegistration> registration = registrationRepository.findById(
                new RegistrationId(offeringId, learnerId));
        if (registration.isEmpty()
                || registration.get().getStatus() != EnrollmentStatus.ENROLLED) {
            return false;
        }
        registrationRepository.delete(registration.get());
        return true;
    }
}
