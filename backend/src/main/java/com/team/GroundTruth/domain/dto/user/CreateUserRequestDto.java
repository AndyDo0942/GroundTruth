package com.team.GroundTruth.domain.dto.user;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.Length;

public record CreateUserRequestDto(
        @NotBlank(message = ERROR_MESSAGE_TITLE_LENGTH)
        @Length(max = 255, message = ERROR_MESSAGE_TITLE_LENGTH)
        String username
) {
    private static final String ERROR_MESSAGE_TITLE_LENGTH = "Invalid username";
}
