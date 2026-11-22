package com.smartcourse.registration.exception;

public class EnrollmentNotFoundException extends RuntimeException {

    public EnrollmentNotFoundException(String learnerId) {
        super("Learner '" + learnerId + "' is not enrolled in this offering.");
    }
}
