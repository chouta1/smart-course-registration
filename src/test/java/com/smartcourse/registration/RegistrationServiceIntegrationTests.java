package com.smartcourse.registration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartcourse.registration.repository.CourseRegistrationRepository;
import com.smartcourse.registration.domain.EnrollmentResult;
import com.smartcourse.registration.domain.EnrollmentStatus;
import com.smartcourse.registration.domain.OfferingSummary;
import com.smartcourse.registration.exception.EnrollmentNotFoundException;
import com.smartcourse.registration.exception.InvalidIdentifierException;
import com.smartcourse.registration.exception.PrerequisiteNotMetException;
import com.smartcourse.registration.service.RegistrationService;
import com.smartcourse.registration.service.PrerequisiteEligibilityPolicy;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "registration.offering-id=course-101",
        "registration.capacity=2"
})
@AutoConfigureMockMvc
@Import(RegistrationServiceIntegrationTests.TestPolicyConfiguration.class)
class RegistrationServiceIntegrationTests {

    private static final String OFFERING_ID = "course-101";

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private CourseRegistrationRepository registrationRepository;

    @Autowired
    private MutablePrerequisiteEligibilityPolicy eligibilityPolicy;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void reset() {
        registrationRepository.deleteAll();
        eligibilityPolicy.setEligibleLearnerIds(Set.of(
                "learner-1",
                "learner-2",
                "learner-3",
                "learner-4",
                "learner-5"));
    }

    @Test
    void enrollsEligibleLearnerWhenCapacityIsAvailable() {
        EnrollmentResult result =
                registrationService.enroll(OFFERING_ID, "learner-1");

        assertEquals(EnrollmentStatus.ENROLLED, result.status());
        assertEquals(1L, offeringSummary().enrolledCount());
        assertEquals(1L, offeringSummary().availableSeats());
    }

    @Test
    void waitlistsEligibleLearnerAndKeepsDuplicateWaitlistPosition() {
        registrationService.enroll(OFFERING_ID, "learner-1");
        registrationService.enroll(OFFERING_ID, "learner-2");

        EnrollmentResult first =
                registrationService.enroll(OFFERING_ID, "learner-3");
        EnrollmentResult duplicate =
                registrationService.enroll(OFFERING_ID, "learner-3");

        assertEquals(EnrollmentStatus.WAITLISTED, first.status());
        assertNotNull(first.waitlistPosition());
        assertEquals(first.waitlistPosition(), duplicate.waitlistPosition());
        assertEquals(1L, offeringSummary().waitlistedCount());
    }

    @Test
    void repeatedDuplicateEnrollmentDoesNotCreateAnotherRecord() {
        EnrollmentResult first =
                registrationService.enroll(OFFERING_ID, "learner-1");
        EnrollmentResult duplicate =
                registrationService.enroll(OFFERING_ID, "learner-1");

        assertEquals(EnrollmentStatus.ENROLLED, first.status());
        assertEquals(EnrollmentStatus.ENROLLED, duplicate.status());
        assertEquals(1L, registrationRepository.count());
        assertEquals(1L, offeringSummary().enrolledCount());
    }

    @Test
    void dropPromotesEarliestEligibleWaitlistedLearner() {
        registrationService.enroll(OFFERING_ID, "learner-1");
        registrationService.enroll(OFFERING_ID, "learner-2");
        eligibilityPolicy.setEligibleLearnerIds(Set.of(
                "learner-1",
                "learner-2",
                "learner-3",
                "learner-4",
                "learner-5"));
        registrationService.enroll(OFFERING_ID, "learner-3");
        registrationService.enroll(OFFERING_ID, "learner-4");
        registrationService.enroll(OFFERING_ID, "learner-5");
        eligibilityPolicy.setEligibleLearnerIds(Set.of(
                "learner-1",
                "learner-2",
                "learner-4",
                "learner-5"));

        com.smartcourse.registration.domain.DropResult result =
                registrationService.drop(OFFERING_ID, "learner-1");

        assertEquals("learner-4", result.promotedLearnerId());
        assertEquals(
                EnrollmentStatus.WAITLISTED,
                registrationRepository
                        .findById(new com.smartcourse.registration.entity.RegistrationId(
                                OFFERING_ID, "learner-3"))
                        .orElseThrow()
                        .getStatus());
        assertEquals(2L, offeringSummary().enrolledCount());
    }

    @Test
    void prerequisiteFailureDoesNotEnrollOrWaitlistLearner() {
        eligibilityPolicy.setEligibleLearnerIds(Set.of("learner-1"));

        assertThrows(
                PrerequisiteNotMetException.class,
                () -> registrationService.enroll(OFFERING_ID, "learner-2"));

        assertEquals(0L, offeringSummary().enrolledCount());
        assertEquals(0L, offeringSummary().waitlistedCount());
    }

    @Test
    void globalExceptionAdvisorReturnsProblemDetailsForInvalidRequest() throws Exception {
        mockMvc.perform(post("/api/v1/offerings/{offeringId}/enrollments", OFFERING_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"learnerId\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(header().exists("X-Request-Id"));

        assertEquals(0L, offeringSummary().enrolledCount());
        assertEquals(0L, offeringSummary().waitlistedCount());
    }

    @Test
    void httpEndpointsExposeSummaryAndEnrollmentLifecycle() throws Exception {
        mockMvc.perform(get("/api/v1/offerings/{offeringId}", OFFERING_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offeringId").value(OFFERING_ID))
                .andExpect(jsonPath("$.capacity").value(2));

        mockMvc.perform(post("/api/v1/offerings/{offeringId}/enrollments", OFFERING_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"learnerId\":\"learner-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENROLLED"));

        mockMvc.perform(delete(
                        "/api/v1/offerings/{offeringId}/enrollments/{learnerId}",
                        OFFERING_ID,
                        "learner-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DROPPED"));
    }

    @Test
    void invalidAndRepeatedDropsDoNotCorruptState() {
        eligibilityPolicy.setEligibleLearnerIds(Set.of("learner-1", "learner-2"));
        registrationService.enroll(OFFERING_ID, "learner-1");
        registrationService.enroll(OFFERING_ID, "learner-2");

        assertThrows(
                InvalidIdentifierException.class,
                () -> registrationService.drop(OFFERING_ID, "invalid learner"));
        assertThrows(
                EnrollmentNotFoundException.class,
                () -> registrationService.drop(OFFERING_ID, "learner-3"));
        registrationService.drop(OFFERING_ID, "learner-1");
        assertThrows(
                EnrollmentNotFoundException.class,
                () -> registrationService.drop(OFFERING_ID, "learner-1"));

        assertEquals(1L, offeringSummary().enrolledCount());
        assertEquals(0L, offeringSummary().waitlistedCount());
    }

    @Test
    void concurrentRequestsNeverExceedCapacity() throws Exception {
        eligibilityPolicy.setEligibleLearnerIds(
                IntStream.rangeClosed(1, 12)
                        .mapToObj(index -> "learner-" + index)
                        .collect(java.util.stream.Collectors.toSet()));

        ExecutorService executor = Executors.newFixedThreadPool(6);
        try {
            java.util.List<Future<EnrollmentResult>> futures =
                    IntStream.rangeClosed(1, 12)
                            .mapToObj(index -> executor.submit(
                                    () -> registrationService.enroll(
                                            OFFERING_ID,
                                            "learner-" + index)))
                            .toList();
            for (Future<EnrollmentResult> future : futures) {
                assertNotNull(future.get());
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(2L, offeringSummary().enrolledCount());
        assertEquals(10L, offeringSummary().waitlistedCount());
    }

    private OfferingSummary offeringSummary() {
        return registrationService.getOfferingSummary(OFFERING_ID);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestPolicyConfiguration {

        @Bean
        @Primary
        MutablePrerequisiteEligibilityPolicy mutablePrerequisiteEligibilityPolicy() {
            return new MutablePrerequisiteEligibilityPolicy();
        }
    }

    static class MutablePrerequisiteEligibilityPolicy
            implements PrerequisiteEligibilityPolicy {

        private volatile Set<String> eligibleLearnerIds = Set.of();

        void setEligibleLearnerIds(Set<String> eligibleLearnerIds) {
            this.eligibleLearnerIds = Set.copyOf(eligibleLearnerIds);
        }

        @Override
        public boolean isEligible(String learnerId) {
            return eligibleLearnerIds.contains(learnerId);
        }
    }

}
