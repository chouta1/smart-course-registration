package com.smartcourse.registration.exception;

public class InvalidIdentifierException extends RuntimeException {

    public InvalidIdentifierException(String fieldName) {
        super(fieldName + " must be 1-64 characters and contain only letters, digits, '.', '_', ':' or '-'.");
    }
}
