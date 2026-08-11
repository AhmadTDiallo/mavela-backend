package com.mavela.backend.customer;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterCustomerRequest(

        @Schema(
                description = "Customer phone number in international E.164 format."
        )
        @NotBlank(message = "PHONE_NUMBER_REQUIRED")
        @Pattern(
                regexp = "^(?:\\s*|\\+[1-9][0-9]{7,14})$",
                message = "PHONE_NUMBER_INVALID_FORMAT"
        )
        String phoneNumber,

        @Schema(
                description = "Optional customer email address."
        )
        @Email(message = "EMAIL_INVALID_FORMAT")
        @Size(
                max = 254,
                message = "EMAIL_TOO_LONG"
        )
        String email,

        @Schema(description = "Customer given name.")
        @NotBlank(message = "FIRST_NAME_REQUIRED")
        @Size(
                max = 100,
                message = "FIRST_NAME_TOO_LONG"
        )
        String firstName,

        @Schema(description = "Customer family name.")
        @NotBlank(message = "LAST_NAME_REQUIRED")
        @Size(
                max = 100,
                message = "LAST_NAME_TOO_LONG"
        )
        String lastName,

        @Schema(
                description = "Preferred language for customer communications.",
                allowableValues = {"en", "fr-CD"}
        )
        @NotBlank(message = "PREFERRED_LOCALE_REQUIRED")
        @Pattern(
                regexp = "^(?:\\s*|en|fr-CD)$",
                message = "PREFERRED_LOCALE_UNSUPPORTED"
        )
        String preferredLocale
) {
}
