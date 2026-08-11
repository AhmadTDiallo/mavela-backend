package com.mavela.backend.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AuthLoginRequest(

        @NotBlank(message = "PHONE_NUMBER_REQUIRED")
        @Pattern(
                regexp = "^\\+[1-9]\\d{7,14}$",
                message = "PHONE_NUMBER_INVALID_FORMAT"
        )
        String phoneNumber,

        @NotBlank(message = "PIN_REQUIRED")
        @Pattern(
                regexp = "^\\d{4}$",
                message = "PIN_INVALID_FORMAT"
        )
        String pin
) {
}