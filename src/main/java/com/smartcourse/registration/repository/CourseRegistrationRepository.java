package com.smartcourse.registration.repository;

import com.smartcourse.registration.domain.EnrollmentStatus;
import com.smartcourse.registration.entity.CourseRegistration;
import com.smartcourse.registration.entity.RegistrationId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseRegistrationRepository
        extends JpaRepository<CourseRegistration, RegistrationId> {

    long countByIdOfferingIdAndStatus(String offeringId, EnrollmentStatus status);

    List<CourseRegistration> findByIdOfferingIdAndStatusOrderByQueueSequenceAsc(
            String offeringId,
            EnrollmentStatus status);

    @Query("""
            SELECT COUNT(registration)
            FROM CourseRegistration registration
            WHERE registration.id.offeringId = :offeringId
              AND registration.status = com.smartcourse.registration.domain.EnrollmentStatus.WAITLISTED
              AND registration.queueSequence <= (
                  SELECT current.queueSequence
                  FROM CourseRegistration current
                  WHERE current.id.offeringId = :offeringId
                    AND current.id.learnerId = :learnerId
              )
            """)
    long countWaitlistPosition(
            @Param("offeringId") String offeringId,
            @Param("learnerId") String learnerId);

    @Query("""
            SELECT COALESCE(MAX(registration.queueSequence), 0)
            FROM CourseRegistration registration
            WHERE registration.id.offeringId = :offeringId
              AND registration.status = com.smartcourse.registration.domain.EnrollmentStatus.WAITLISTED
            """)
    long findMaxWaitlistSequence(@Param("offeringId") String offeringId);
}
