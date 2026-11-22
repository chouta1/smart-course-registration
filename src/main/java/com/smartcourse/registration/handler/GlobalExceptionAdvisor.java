package com.smartcourse.registration.handler;

import com.smartcourse.registration.exception.EnrollmentNotFoundException;
import com.smartcourse.registration.exception.InvalidIdentifierException;
import com.smartcourse.registration.exception.OfferingNotFoundException;
import com.smartcourse.registration.exception.PrerequisiteNotMetException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionAdvisor {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(GlobalExceptionAdvisor.class);

    @ExceptionHandler(InvalidIdentifierException.class)
    public ResponseEntity<ProblemDetail> handleInvalidIdentifier(
            InvalidIdentifierException exception,
            HttpServletRequest request) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST",
                exception.getMessage(),
                request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        ProblemDetail problem = createProblem(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST",
                "One or more request fields are invalid.",
                request);
        List<String> fieldErrors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .distinct()
                .toList();
        problem.setProperty("errors", fieldErrors);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadableRequest(
            HttpMessageNotReadableException exception,
            HttpServletRequest request) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST",
                "The request body is missing or is not valid JSON.",
                request);
    }

    @ExceptionHandler(OfferingNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleOfferingNotFound(
            OfferingNotFoundException exception,
            HttpServletRequest request) {
        return problem(
                HttpStatus.NOT_FOUND,
                "OFFERING_NOT_FOUND",
                exception.getMessage(),
                request);
    }

    @ExceptionHandler(EnrollmentNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleEnrollmentNotFound(
            EnrollmentNotFoundException exception,
            HttpServletRequest request) {
        return problem(
                HttpStatus.NOT_FOUND,
                "ENROLLMENT_NOT_FOUND",
                exception.getMessage(),
                request);
    }

    @ExceptionHandler(PrerequisiteNotMetException.class)
    public ResponseEntity<ProblemDetail> handlePrerequisiteNotMet(
            PrerequisiteNotMetException exception,
            HttpServletRequest request) {
        return problem(
                HttpStatus.UNPROCESSABLE_CONTENT,
                "PREREQUISITE_NOT_MET",
                exception.getMessage(),
                request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrityViolation(
            DataIntegrityViolationException exception,
            HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT,
                "STATE_CONFLICT",
                "The requested change conflicts with the current registration state.",
                request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(
            Exception exception,
            HttpServletRequest request) {
        LOGGER.error("Unhandled request error. requestId={}", MDC.get("requestId"), exception);
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "An unexpected error occurred.",
                request);
    }

    private ResponseEntity<ProblemDetail> problem(
            HttpStatus status,
            String code,
            String detail,
            HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(createProblem(status, code, detail, request));
    }

    private ProblemDetail createProblem(
            HttpStatus status,
            String code,
            String detail,
            HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setDetail(detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        String requestId = MDC.get("requestId");
        if (requestId != null) {
            problem.setProperty("requestId", requestId);
        }
        return problem;
    }
}
