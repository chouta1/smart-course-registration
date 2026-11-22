package com.smartcourse.registration.domain;

import com.smartcourse.registration.exception.InvalidIdentifierException;
import java.util.regex.Pattern;

public final class IdentifierValidator {

    public static final String PATTERN =
            "^[A-Za-z0-9][A-Za-z0-9._:-]{0,63}$";

    private static final Pattern IDENTIFIER_PATTERN =
            Pattern.compile(PATTERN);

    private IdentifierValidator() {
    }

    public static void requireValid(String value, String fieldName) {
        if (value == null
                || value.isBlank()
                || !IDENTIFIER_PATTERN.matcher(value).matches()) {
            throw new InvalidIdentifierException(fieldName);
        }
    }
}
