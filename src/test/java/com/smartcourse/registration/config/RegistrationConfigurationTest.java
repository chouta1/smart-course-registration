package com.smartcourse.registration.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartcourse.registration.dao.CourseOfferingEntityProjection;
import com.smartcourse.registration.dao.RegistrationDao;
import com.smartcourse.registration.service.ConfiguredPrerequisiteEligibilityPolicy;
import com.smartcourse.registration.service.PrerequisiteEligibilityPolicy;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class RegistrationConfigurationTest {

    @Test
    void propertiesExposeValidDemoDefaultsAndCanBeOverridden() {
        RegistrationProperties properties = new RegistrationProperties();

        assertEquals("course-101", properties.getOfferingId());
        assertEquals(2, properties.getCapacity());
        assertEquals(
                Set.of("learner-1", "learner-2", "learner-3", "learner-4", "learner-5"),
                properties.getEligibleLearnerIds());

        properties.setOfferingId("course-202");
        properties.setCapacity(4);
        properties.setEligibleLearnerIds(Set.of("learner-x"));
        assertEquals("course-202", properties.getOfferingId());
        assertEquals(4, properties.getCapacity());
        assertEquals(Set.of("learner-x"), properties.getEligibleLearnerIds());
    }

    @Test
    void createsPrerequisitePolicyFromConfiguredEligibleLearners() {
        RegistrationProperties properties = new RegistrationProperties();
        properties.setEligibleLearnerIds(Set.of("learner-allowed"));
        PrerequisiteEligibilityPolicy policy =
                new RegistrationConfiguration().prerequisiteEligibilityPolicy(properties);

        assertEquals(true, policy.isEligible("learner-allowed"));
        assertFalse(policy.isEligible("learner-denied"));
        assertEquals(
                true,
                new ConfiguredPrerequisiteEligibilityPolicy(Set.of("learner-a"))
                        .isEligible("learner-a"));
    }

    @Test
    void initializesOfferingOnlyWhenItDoesNotAlreadyExist() {
        RegistrationDao dao = org.mockito.Mockito.mock(RegistrationDao.class);
        RegistrationProperties properties = new RegistrationProperties();
        OfferingInitializer initializer = new OfferingInitializer(dao, properties);
        when(dao.findOfferingForUpdate("course-101")).thenReturn(Optional.empty());

        initializer.run(new DefaultApplicationArguments(new String[0]));

        verify(dao).createOffering("course-101", 2);
    }

    @Test
    void doesNotOverwriteExistingOfferingDuringStartup() {
        RegistrationDao dao = org.mockito.Mockito.mock(RegistrationDao.class);
        RegistrationProperties properties = new RegistrationProperties();
        OfferingInitializer initializer = new OfferingInitializer(dao, properties);
        when(dao.findOfferingForUpdate("course-101"))
                .thenReturn(Optional.of(
                        new CourseOfferingEntityProjection("course-101", 2)));

        initializer.run(new DefaultApplicationArguments(new String[0]));

        verify(dao).findOfferingForUpdate("course-101");
        org.mockito.Mockito.verify(dao, org.mockito.Mockito.never())
                .createOffering(org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyInt());
    }
}
