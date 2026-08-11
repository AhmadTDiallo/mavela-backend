package com.mavela.backend.customer;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterCustomerRequest(

        @NotBlank(message = "PHONE_NUMBER_REQUIRED")
        @Pattern(
                regexp = "^(?:\\s*|\\+[1-9][0-9]{7,14})$",
                message = "PHONE_NUMBER_INVALID_FORMAT"
        )
        String phoneNumber,

        @Email(message = "EMAIL_INVALID_FORMAT")
        @Size(
                max = 254,
                message = "EMAIL_TOO_LONG"
        )
        String email,

        @NotBlank(message = "FIRST_NAME_REQUIRED")
        @Size(
                max = 100,
                message = "FIRST_NAME_TOO_LONG"
        )
        String firstName,

        @NotBlank(message = "LAST_NAME_REQUIRED")
        @Size(
                max = 100,
                message = "LAST_NAME_TOO_LONG"
        )
        String lastName,

        @NotBlank(message = "PREFERRED_LOCALE_REQUIRED")
        @Pattern(
                regexp = "^(?:\\s*|en|fr-CD)$",
                message = "PREFERRED_LOCALE_UNSUPPORTED"
        )
        String preferredLocale
) {
}