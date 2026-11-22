package com.smartcourse.registration.service;

import java.util.Set;

public final class ConfiguredPrerequisiteEligibilityPolicy
        implements PrerequisiteEligibilityPolicy {

    private final Set<String> eligibleLearnerIds;

    public ConfiguredPrerequisiteEligibilityPolicy(Set<String> eligibleLearnerIds) {
        this.eligibleLearnerIds = Set.copyOf(eligibleLearnerIds);
    }

    @Override
    public boolean isEligible(String learnerId) {
        return eligibleLearnerIds.contains(learnerId);
    }
}
