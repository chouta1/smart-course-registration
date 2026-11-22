package com.smartcourse.registration.exception;

public class PrerequisiteNotMetException extends RuntimeException {

    public PrerequisiteNotMetException(String learnerId) {
        super("Learner '" + learnerId + "' does not meet the prerequisite requirement.");
    }
}
