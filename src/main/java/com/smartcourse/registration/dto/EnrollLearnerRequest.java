package com.smartcourse.registration.dto;

import com.smartcourse.registration.domain.IdentifierValidator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EnrollLearnerRequest(
        @NotBlank
        @Size(max = 64)
        @Pattern(regexp = IdentifierValidator.PATTERN)
        String learnerId) {
}
