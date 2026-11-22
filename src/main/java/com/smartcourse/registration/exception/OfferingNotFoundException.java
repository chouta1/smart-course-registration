package com.smartcourse.registration.exception;

public class OfferingNotFoundException extends RuntimeException {

    public OfferingNotFoundException(String offeringId) {
        super("Offering '" + offeringId + "' was not found.");
    }
}
