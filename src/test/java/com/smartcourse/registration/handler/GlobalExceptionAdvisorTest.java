package com.smartcourse.registration.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.smartcourse.registration.exception.EnrollmentNotFoundException;
import com.smartcourse.registration.exception.InvalidIdentifierException;
import com.smartcourse.registration.exception.OfferingNotFoundException;
import com.smartcourse.registration.exception.PrerequisiteNotMetException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;

class GlobalExceptionAdvisorTest {

    private GlobalExceptionAdvisor advisor;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        advisor = new GlobalExceptionAdvisor();
        request = new MockHttpServletRequest("POST", "/api/v1/offerings/course-101/enrollments");
        request.setRequestURI("/api/v1/offerings/course-101/enrollments");
    }

    @Test
    void mapsInvalidIdentifierToBadRequestProblem() {
        ResponseEntity<ProblemDetail> response = advisor.handleInvalidIdentifier(
                new InvalidIdentifierException("learnerId"),
                request);

        assertProblem(response, HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
    }

    @Test
    void mapsBeanValidationErrorsToProblemFieldErrors() {
        BeanPropertyBindingResult bindingResult =
                new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError(
                "request", "learnerId", "", false, null, null, "must not be blank"));
        MethodArgumentNotValidException exception =
                new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<ProblemDetail> response =
                advisor.handleValidation(exception, request);

        assertProblem(response, HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
        assertEquals(
                java.util.List.of("learnerId: must not be blank"),
                response.getBody().getProperties().get("errors"));
    }

    @Test
    void mapsUnreadableBodyToBadRequestProblem() {
        ResponseEntity<ProblemDetail> response = advisor.handleUnreadableRequest(
                new HttpMessageNotReadableException(
                        "bad body", new MockHttpInputMessage(new byte[0])),
                request);

        assertProblem(response, HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
    }

    @Test
    void mapsDomainNotFoundAndPrerequisiteExceptions() {
        assertProblem(
                advisor.handleOfferingNotFound(
                        new OfferingNotFoundException("missing"), request),
                HttpStatus.NOT_FOUND,
                "OFFERING_NOT_FOUND");
        assertProblem(
                advisor.handleEnrollmentNotFound(
                        new EnrollmentNotFoundException("learner-1"), request),
                HttpStatus.NOT_FOUND,
                "ENROLLMENT_NOT_FOUND");
        assertProblem(
                advisor.handlePrerequisiteNotMet(
                        new PrerequisiteNotMetException("learner-1"), request),
                HttpStatus.UNPROCESSABLE_CONTENT,
                "PREREQUISITE_NOT_MET");
    }

    @Test
    void mapsPersistenceConflictToConflictProblem() {
        ResponseEntity<ProblemDetail> response = advisor.handleDataIntegrityViolation(
                new DataIntegrityViolationException("duplicate"),
                request);

        assertProblem(response, HttpStatus.CONFLICT, "STATE_CONFLICT");
    }

    @Test
    void mapsUnexpectedExceptionToGenericInternalError() {
        ResponseEntity<ProblemDetail> response =
                advisor.handleUnexpected(new IllegalStateException("internal"), request);

        assertProblem(response, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR");
        assertEquals(
                "An unexpected error occurred.",
                response.getBody().getDetail());
    }

    @Test
    void includesRequestIdWhenPresentInMdc() {
        org.slf4j.MDC.put("requestId", "request-123");
        try {
            ResponseEntity<ProblemDetail> response = advisor.handleInvalidIdentifier(
                    new InvalidIdentifierException("learnerId"),
                    request);

            assertEquals(
                    "request-123",
                    response.getBody().getProperties().get("requestId"));
        } finally {
            org.slf4j.MDC.remove("requestId");
        }
    }

    private static void assertProblem(
            ResponseEntity<ProblemDetail> response,
            HttpStatus status,
            String code) {
        assertEquals(status, response.getStatusCode());
        assertEquals(status.value(), response.getBody().getStatus());
        assertEquals(code, response.getBody().getProperties().get("code"));
        assertEquals(
                "/api/v1/offerings/course-101/enrollments",
                response.getBody().getInstance().toString());
    }
}
