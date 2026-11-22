package com.smartcourse.registration.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartcourse.registration.dao.CourseOfferingEntityProjection;
import com.smartcourse.registration.dao.RegistrationDao;
import com.smartcourse.registration.domain.DropResult;
import com.smartcourse.registration.domain.EnrollmentResult;
import com.smartcourse.registration.domain.EnrollmentStatus;
import com.smartcourse.registration.domain.OfferingSummary;
import com.smartcourse.registration.exception.EnrollmentNotFoundException;
import com.smartcourse.registration.exception.InvalidIdentifierException;
import com.smartcourse.registration.exception.OfferingNotFoundException;
import com.smartcourse.registration.exception.PrerequisiteNotMetException;
import com.smartcourse.registration.service.impl.RegistrationServiceImpl;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RegistrationServiceImplTest {

    private static final String OFFERING_ID = "course-101";
    private static final String LEARNER_ID = "learner-1";

    private RegistrationDao registrationDao;
    private PrerequisiteEligibilityPolicy eligibilityPolicy;
    private RegistrationServiceImpl service;

    @BeforeEach
    void setUp() {
        registrationDao = org.mockito.Mockito.mock(RegistrationDao.class);
        eligibilityPolicy = org.mockito.Mockito.mock(PrerequisiteEligibilityPolicy.class);
        service = new RegistrationServiceImpl(registrationDao, eligibilityPolicy);
    }

    @Test
    void enrollsEligibleLearnerWhenSeatIsAvailable() {
        givenOffering(2);
        when(registrationDao.findStatus(OFFERING_ID, LEARNER_ID))
                .thenReturn(Optional.empty(), Optional.of(EnrollmentStatus.ENROLLED));
        when(eligibilityPolicy.isEligible(LEARNER_ID)).thenReturn(true);
        when(registrationDao.countEnrolled(OFFERING_ID)).thenReturn(0L);
        when(registrationDao.findWaitlistedLearnerIds(OFFERING_ID)).thenReturn(List.of());

        EnrollmentResult result = service.enroll(OFFERING_ID, LEARNER_ID);

        assertEquals(EnrollmentStatus.ENROLLED, result.status());
        assertNull(result.waitlistPosition());
        verify(registrationDao).enroll(OFFERING_ID, LEARNER_ID);
    }

    @Test
    void returnsExistingEnrollmentWithoutChangingState() {
        givenOffering(2);
        when(registrationDao.findStatus(OFFERING_ID, LEARNER_ID))
                .thenReturn(Optional.of(EnrollmentStatus.ENROLLED));

        EnrollmentResult result = service.enroll(OFFERING_ID, LEARNER_ID);

        assertEquals(EnrollmentStatus.ENROLLED, result.status());
        verify(registrationDao, never()).enroll(OFFERING_ID, LEARNER_ID);
        verify(eligibilityPolicy, never()).isEligible(LEARNER_ID);
    }

    @Test
    void returnsExistingWaitlistPositionWithoutChangingQueue() {
        givenOffering(2);
        when(registrationDao.findStatus(OFFERING_ID, LEARNER_ID))
                .thenReturn(Optional.of(EnrollmentStatus.WAITLISTED));
        when(registrationDao.findWaitlistPosition(OFFERING_ID, LEARNER_ID))
                .thenReturn(Optional.of(2L));

        EnrollmentResult result = service.enroll(OFFERING_ID, LEARNER_ID);

        assertEquals(EnrollmentStatus.WAITLISTED, result.status());
        assertEquals(2L, result.waitlistPosition());
        verify(registrationDao, never()).waitlist(OFFERING_ID, LEARNER_ID);
    }

    @Test
    void rejectsIneligibleLearnerWithoutWritingState() {
        givenOffering(2);
        when(registrationDao.findStatus(OFFERING_ID, LEARNER_ID))
                .thenReturn(Optional.empty());
        when(eligibilityPolicy.isEligible(LEARNER_ID)).thenReturn(false);

        assertThrows(
                PrerequisiteNotMetException.class,
                () -> service.enroll(OFFERING_ID, LEARNER_ID));

        verify(registrationDao, never()).enroll(OFFERING_ID, LEARNER_ID);
        verify(registrationDao, never()).waitlist(OFFERING_ID, LEARNER_ID);
    }

    @Test
    void addsEligibleLearnerToWaitlistWhenOfferingIsFull() {
        givenOffering(1);
        when(registrationDao.findStatus(OFFERING_ID, LEARNER_ID))
                .thenReturn(Optional.empty(), Optional.of(EnrollmentStatus.WAITLISTED));
        when(eligibilityPolicy.isEligible(LEARNER_ID)).thenReturn(true);
        when(registrationDao.countEnrolled(OFFERING_ID)).thenReturn(1L);
        when(registrationDao.findWaitlistedLearnerIds(OFFERING_ID)).thenReturn(List.of());
        when(registrationDao.findWaitlistPosition(OFFERING_ID, LEARNER_ID))
                .thenReturn(Optional.of(1L));

        EnrollmentResult result = service.enroll(OFFERING_ID, LEARNER_ID);

        assertEquals(EnrollmentStatus.WAITLISTED, result.status());
        assertEquals(1L, result.waitlistPosition());
        verify(registrationDao).waitlist(OFFERING_ID, LEARNER_ID);
    }

    @Test
    void doesNotBypassOlderIneligibleWaiterWhenSeatIsOpen() {
        givenOffering(2);
        when(registrationDao.findStatus(OFFERING_ID, LEARNER_ID))
                .thenReturn(Optional.empty(), Optional.of(EnrollmentStatus.WAITLISTED));
        when(eligibilityPolicy.isEligible(LEARNER_ID)).thenReturn(true);
        when(registrationDao.countEnrolled(OFFERING_ID)).thenReturn(1L, 1L, 2L);
        when(registrationDao.findWaitlistedLearnerIds(OFFERING_ID))
                .thenReturn(List.of("learner-ineligible", LEARNER_ID));
        when(eligibilityPolicy.isEligible("learner-ineligible")).thenReturn(false);
        when(registrationDao.promote(OFFERING_ID, LEARNER_ID)).thenReturn(true);
        when(registrationDao.findWaitlistPosition(OFFERING_ID, LEARNER_ID))
                .thenReturn(Optional.of(1L));

        EnrollmentResult result = service.enroll(OFFERING_ID, LEARNER_ID);

        assertEquals(EnrollmentStatus.WAITLISTED, result.status());
        verify(registrationDao).promote(OFFERING_ID, LEARNER_ID);
        verify(registrationDao, never()).promote(OFFERING_ID, "learner-ineligible");
    }

    @Test
    void rejectsInvalidIdentifiersBeforeCallingDao() {
        assertThrows(
                InvalidIdentifierException.class,
                () -> service.enroll(OFFERING_ID, "invalid learner"));
        verify(registrationDao, never()).findOfferingForUpdate(OFFERING_ID);
    }

    @Test
    void reportsUnknownOfferingForEnrollmentAndSummary() {
        when(registrationDao.findOfferingForUpdate(OFFERING_ID)).thenReturn(Optional.empty());

        assertThrows(
                OfferingNotFoundException.class,
                () -> service.enroll(OFFERING_ID, LEARNER_ID));
        assertThrows(
                OfferingNotFoundException.class,
                () -> service.getOfferingSummary(OFFERING_ID));
    }

    @Test
    void throwsIfEnrollmentStateWasNotPersisted() {
        givenOffering(2);
        when(registrationDao.findStatus(OFFERING_ID, LEARNER_ID))
                .thenReturn(Optional.empty(), Optional.empty());
        when(eligibilityPolicy.isEligible(LEARNER_ID)).thenReturn(true);
        when(registrationDao.countEnrolled(OFFERING_ID)).thenReturn(0L);
        when(registrationDao.findWaitlistedLearnerIds(OFFERING_ID)).thenReturn(List.of());

        assertThrows(
                IllegalStateException.class,
                () -> service.enroll(OFFERING_ID, LEARNER_ID));
    }

    @Test
    void dropsEnrollmentAndPromotesEarliestEligibleLearnerAfterSkippingIneligible() {
        givenOffering(2);
        when(registrationDao.drop(OFFERING_ID, LEARNER_ID)).thenReturn(true);
        when(registrationDao.findWaitlistedLearnerIds(OFFERING_ID))
                .thenReturn(List.of("learner-ineligible", "learner-2"));
        when(eligibilityPolicy.isEligible("learner-ineligible")).thenReturn(false);
        when(eligibilityPolicy.isEligible("learner-2")).thenReturn(true);
        when(registrationDao.promote(OFFERING_ID, "learner-2")).thenReturn(true);

        DropResult result = service.drop(OFFERING_ID, LEARNER_ID);

        assertEquals("learner-2", result.promotedLearnerId());
        verify(registrationDao).promote(OFFERING_ID, "learner-2");
    }

    @Test
    void leavesSeatOpenWhenNobodyIsEligibleForPromotion() {
        givenOffering(2);
        when(registrationDao.drop(OFFERING_ID, LEARNER_ID)).thenReturn(true);
        when(registrationDao.findWaitlistedLearnerIds(OFFERING_ID))
                .thenReturn(List.of("learner-ineligible"));
        when(eligibilityPolicy.isEligible("learner-ineligible")).thenReturn(false);

        DropResult result = service.drop(OFFERING_ID, LEARNER_ID);

        assertNull(result.promotedLearnerId());
        verify(registrationDao, never()).promote(OFFERING_ID, "learner-ineligible");
    }

    @Test
    void repeatedDropReturnsNotFoundWithoutPromotion() {
        givenOffering(2);
        when(registrationDao.drop(OFFERING_ID, LEARNER_ID)).thenReturn(false);

        assertThrows(
                EnrollmentNotFoundException.class,
                () -> service.drop(OFFERING_ID, LEARNER_ID));

        verify(registrationDao, never()).findWaitlistedLearnerIds(OFFERING_ID);
    }

    @Test
    void returnsOfferingSummaryWhenFound() {
        OfferingSummary summary = new OfferingSummary(OFFERING_ID, 2, 1, 1, 0);
        when(registrationDao.findOfferingSummary(OFFERING_ID))
                .thenReturn(Optional.of(summary));

        assertEquals(summary, service.getOfferingSummary(OFFERING_ID));
    }

    private void givenOffering(int capacity) {
        when(registrationDao.findOfferingForUpdate(OFFERING_ID))
                .thenReturn(Optional.of(
                        new CourseOfferingEntityProjection(OFFERING_ID, capacity)));
    }
}
