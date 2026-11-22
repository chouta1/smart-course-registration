package com.smartcourse.registration.repository;

import com.smartcourse.registration.entity.CourseOffering;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseOfferingRepository extends JpaRepository<CourseOffering, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT offering FROM CourseOffering offering WHERE offering.offeringId = :offeringId")
    Optional<CourseOffering> findByIdForUpdate(@Param("offeringId") String offeringId);
}
