package com.smartcourse.registration.config;

import com.smartcourse.registration.service.ConfiguredPrerequisiteEligibilityPolicy;
import com.smartcourse.registration.service.PrerequisiteEligibilityPolicy;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RegistrationProperties.class)
public class RegistrationConfiguration {

    @Bean
    @ConditionalOnMissingBean(PrerequisiteEligibilityPolicy.class)
    PrerequisiteEligibilityPolicy prerequisiteEligibilityPolicy(
            RegistrationProperties properties) {
        Set<String> eligibleLearnerIds =
                new java.util.LinkedHashSet<>(properties.getEligibleLearnerIds());
        return new ConfiguredPrerequisiteEligibilityPolicy(eligibleLearnerIds);
    }
}
