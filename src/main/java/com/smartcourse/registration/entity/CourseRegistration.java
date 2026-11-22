package com.smartcourse.registration.entity;

import com.smartcourse.registration.domain.EnrollmentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "course_registration",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_registration_offering_learner",
                columnNames = {"offering_id", "learner_id"}))
public class CourseRegistration {

    @EmbeddedId
    private RegistrationId id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EnrollmentStatus status;

    @Column(name = "queue_sequence")
    private Long queueSequence;

    protected CourseRegistration() {
    }

    private CourseRegistration(
            RegistrationId id,
            EnrollmentStatus status,
            Long queueSequence) {
        this.id = id;
        this.status = status;
        this.queueSequence = queueSequence;
    }

    public static CourseRegistration enrolled(String offeringId, String learnerId) {
        return new CourseRegistration(
                new RegistrationId(offeringId, learnerId),
                EnrollmentStatus.ENROLLED,
                null);
    }

    public static CourseRegistration waitlisted(
            String offeringId,
            String learnerId,
            long queueSequence) {
        return new CourseRegistration(
                new RegistrationId(offeringId, learnerId),
                EnrollmentStatus.WAITLISTED,
                queueSequence);
    }

    public RegistrationId getId() {
        return id;
    }

    public EnrollmentStatus getStatus() {
        return status;
    }

    public Long getQueueSequence() {
        return queueSequence;
    }

    public void promote() {
        status = EnrollmentStatus.ENROLLED;
        queueSequence = null;
    }
}
