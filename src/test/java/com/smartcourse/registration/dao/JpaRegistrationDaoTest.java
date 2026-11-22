package com.smartcourse.registration.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartcourse.registration.domain.EnrollmentStatus;
import com.smartcourse.registration.entity.CourseOffering;
import com.smartcourse.registration.entity.CourseRegistration;
import com.smartcourse.registration.entity.RegistrationId;
import com.smartcourse.registration.repository.CourseOfferingRepository;
import com.smartcourse.registration.repository.CourseRegistrationRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class JpaRegistrationDaoTest {

    private CourseOfferingRepository offeringRepository;
    private CourseRegistrationRepository registrationRepository;
    private JpaRegistrationDao dao;

    @BeforeEach
    void setUp() {
        offeringRepository = org.mockito.Mockito.mock(CourseOfferingRepository.class);
        registrationRepository = org.mockito.Mockito.mock(CourseRegistrationRepository.class);
        dao = new JpaRegistrationDao(offeringRepository, registrationRepository);
    }

    @Test
    void mapsOfferingSummaryAndCounts() {
        when(offeringRepository.findById("course-101"))
                .thenReturn(Optional.of(new CourseOffering("course-101", 3)));
        when(registrationRepository.countByIdOfferingIdAndStatus(
                "course-101", EnrollmentStatus.ENROLLED)).thenReturn(2L);
        when(registrationRepository.countByIdOfferingIdAndStatus(
                "course-101", EnrollmentStatus.WAITLISTED)).thenReturn(4L);

        assertEquals(
                new com.smartcourse.registration.domain.OfferingSummary(
                        "course-101", 3, 2, 1, 4),
                dao.findOfferingSummary("course-101").orElseThrow());
    }

    @Test
    void returnsEmptySummaryForUnknownOffering() {
        when(offeringRepository.findById("missing")).thenReturn(Optional.empty());

        assertTrue(dao.findOfferingSummary("missing").isEmpty());
        verify(registrationRepository, never()).countByIdOfferingIdAndStatus(
                any(), any());
    }

    @Test
    void mapsLockedOfferingAndCreatesOffering() {
        when(offeringRepository.findByIdForUpdate("course-101"))
                .thenReturn(Optional.of(new CourseOffering("course-101", 2)));
        when(offeringRepository.save(any(CourseOffering.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals(
                new CourseOfferingEntityProjection("course-101", 2),
                dao.findOfferingForUpdate("course-101").orElseThrow());
        assertEquals(
                new CourseOfferingEntityProjection("course-102", 5),
                dao.createOffering("course-102", 5));
    }

    @Test
    void mapsRegistrationStatusAndCountQueries() {
        when(registrationRepository.findById(new RegistrationId("course-101", "learner-1")))
                .thenReturn(Optional.of(CourseRegistration.enrolled(
                        "course-101", "learner-1")));
        when(registrationRepository.countByIdOfferingIdAndStatus(
                "course-101", EnrollmentStatus.ENROLLED)).thenReturn(1L);
        when(registrationRepository.countByIdOfferingIdAndStatus(
                "course-101", EnrollmentStatus.WAITLISTED)).thenReturn(2L);

        assertEquals(
                Optional.of(EnrollmentStatus.ENROLLED),
                dao.findStatus("course-101", "learner-1"));
        assertEquals(1L, dao.countEnrolled("course-101"));
        assertEquals(2L, dao.countWaitlisted("course-101"));
    }

    @Test
    void mapsWaitlistedLearnersToFifoIds() {
        when(registrationRepository.findByIdOfferingIdAndStatusOrderByQueueSequenceAsc(
                "course-101", EnrollmentStatus.WAITLISTED))
                .thenReturn(List.of(
                        CourseRegistration.waitlisted("course-101", "learner-1", 1),
                        CourseRegistration.waitlisted("course-101", "learner-2", 2)));

        assertEquals(
                List.of("learner-1", "learner-2"),
                dao.findWaitlistedLearnerIds("course-101"));
    }

    @Test
    void returnsWaitlistPositionOnlyForWaitlistedLearners() {
        when(registrationRepository.findById(new RegistrationId("course-101", "learner-1")))
                .thenReturn(Optional.of(CourseRegistration.waitlisted(
                        "course-101", "learner-1", 1)));
        when(registrationRepository.countWaitlistPosition("course-101", "learner-1"))
                .thenReturn(3L);

        assertEquals(Optional.of(3L), dao.findWaitlistPosition("course-101", "learner-1"));

        when(registrationRepository.findById(new RegistrationId("course-101", "learner-2")))
                .thenReturn(Optional.of(CourseRegistration.enrolled(
                        "course-101", "learner-2")));
        assertTrue(dao.findWaitlistPosition("course-101", "learner-2").isEmpty());
        assertTrue(dao.findWaitlistPosition("course-101", "learner-3").isEmpty());
        verify(registrationRepository, never()).countWaitlistPosition(
                "course-101", "learner-2");
    }

    @Test
    void persistsEnrollmentAndAssignsIncreasingQueueSequence() {
        when(registrationRepository.findMaxWaitlistSequence("course-101")).thenReturn(4L);

        dao.enroll("course-101", "learner-1");
        dao.waitlist("course-101", "learner-2");

        ArgumentCaptor<CourseRegistration> registrations =
                ArgumentCaptor.forClass(CourseRegistration.class);
        verify(registrationRepository, times(2)).save(registrations.capture());
        assertEquals(EnrollmentStatus.ENROLLED, registrations.getAllValues().get(0).getStatus());
        assertEquals(EnrollmentStatus.WAITLISTED, registrations.getAllValues().get(1).getStatus());
        assertEquals(5L, registrations.getAllValues().get(1).getQueueSequence());
        verify(registrationRepository).findMaxWaitlistSequence("course-101");
    }

    @Test
    void promotesOnlyWaitlistedRecord() {
        CourseRegistration waitlisted =
                CourseRegistration.waitlisted("course-101", "learner-1", 1);
        when(registrationRepository.findById(new RegistrationId("course-101", "learner-1")))
                .thenReturn(Optional.of(waitlisted));

        assertTrue(dao.promote("course-101", "learner-1"));
        assertEquals(EnrollmentStatus.ENROLLED, waitlisted.getStatus());
        assertEquals(null, waitlisted.getQueueSequence());
        verify(registrationRepository).save(waitlisted);

        CourseRegistration enrolled =
                CourseRegistration.enrolled("course-101", "learner-2");
        when(registrationRepository.findById(new RegistrationId("course-101", "learner-2")))
                .thenReturn(Optional.of(enrolled));
        assertFalse(dao.promote("course-101", "learner-2"));
        assertFalse(dao.promote("course-101", "learner-missing"));
    }

    @Test
    void dropsOnlyEnrolledRecords() {
        CourseRegistration enrolled =
                CourseRegistration.enrolled("course-101", "learner-1");
        when(registrationRepository.findById(new RegistrationId("course-101", "learner-1")))
                .thenReturn(Optional.of(enrolled));

        assertTrue(dao.drop("course-101", "learner-1"));
        verify(registrationRepository).delete(enrolled);

        when(registrationRepository.findById(new RegistrationId("course-101", "learner-2")))
                .thenReturn(Optional.of(CourseRegistration.waitlisted(
                        "course-101", "learner-2", 1)));
        when(registrationRepository.findById(new RegistrationId("course-101", "learner-3")))
                .thenReturn(Optional.empty());

        assertFalse(dao.drop("course-101", "learner-2"));
        assertFalse(dao.drop("course-101", "learner-3"));
    }
}
