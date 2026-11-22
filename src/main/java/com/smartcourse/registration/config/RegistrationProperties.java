package com.smartcourse.registration.config;

import com.smartcourse.registration.domain.IdentifierValidator;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "registration")
public class RegistrationProperties {

    @NotBlank
    @Pattern(regexp = IdentifierValidator.PATTERN)
    private String offeringId = "course-101";

    @Min(1)
    private int capacity = 2;

    @NotNull
    private Set<String> eligibleLearnerIds = new LinkedHashSet<>(Set.of(
            "learner-1",
            "learner-2",
            "learner-3",
            "learner-4",
            "learner-5"));

    public String getOfferingId() {
        return offeringId;
    }

    public void setOfferingId(String offeringId) {
        this.offeringId = offeringId;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public Set<String> getEligibleLearnerIds() {
        return eligibleLearnerIds;
    }

    public void setEligibleLearnerIds(Set<String> eligibleLearnerIds) {
        this.eligibleLearnerIds = eligibleLearnerIds;
    }
}
