package com.mavela.backend.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AuthLoginRequest(

        @Schema(
                description = "Customer phone number in international E.164 format."
        )
        @NotBlank(message = "PHONE_NUMBER_REQUIRED")
        @Pattern(
                regexp = "^\\+[1-9]\\d{7,14}$",
                message = "PHONE_NUMBER_INVALID_FORMAT"
        )
        String phoneNumber,

        @Schema(
                description = "Customer's four-digit PIN.",
                accessMode = Schema.AccessMode.WRITE_ONLY
        )
        @NotBlank(message = "PIN_REQUIRED")
        @Pattern(
                regexp = "^\\d{4}$",
                message = "PIN_INVALID_FORMAT"
        )
        String pin
) {
}
