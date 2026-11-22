package com.smartcourse.registration.service;

public interface PrerequisiteEligibilityPolicy {

    boolean isEligible(String learnerId);
}
