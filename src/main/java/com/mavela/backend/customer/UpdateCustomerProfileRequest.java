package com.mavela.backend.customer;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateCustomerProfileRequest(

        @Schema(description = "Customer given name.")
        @Pattern(
                regexp = ".*\\S.*",
                message = "FIRST_NAME_REQUIRED"
        )
        @Size(
                max = 100,
                message = "FIRST_NAME_TOO_LONG"
        )
        String firstName,

        @Schema(description = "Customer family name.")
        @Pattern(
                regexp = ".*\\S.*",
                message = "LAST_NAME_REQUIRED"
        )
        @Size(
                max = 100,
                message = "LAST_NAME_TOO_LONG"
        )
        String lastName,

        @Schema(
                description = "Preferred language for customer communications.",
                allowableValues = {"fr", "en", "ln", "sw"}
        )
        @Pattern(
                regexp = "^\\s*(?:fr|en|ln|sw)\\s*$",
                message = "PREFERRED_LOCALE_UNSUPPORTED"
        )
        String preferredLocale,

        @Schema(description = "Customer date of birth.")
        @Past(message = "DATE_OF_BIRTH_MUST_BE_IN_THE_PAST")
        LocalDate dateOfBirth,

        @Schema(description = "Two-letter nationality code.")
        @Pattern(
                regexp = "(?i)^\\s*[a-z]{2}\\s*$",
                message = "NATIONALITY_INVALID_FORMAT"
        )
        String nationality,

        @Schema(description = "Customer gender.")
        Gender gender,

        @Schema(description = "Customer residential address.")
        @Pattern(
                regexp = ".*\\S.*",
                message = "ADDRESS_LINE_REQUIRED"
        )
        @Size(
                max = 200,
                message = "ADDRESS_LINE_TOO_LONG"
        )
        String addressLine,

        @Schema(description = "Customer city of residence.")
        @Pattern(
                regexp = ".*\\S.*",
                message = "CITY_REQUIRED"
        )
        @Size(
                max = 100,
                message = "CITY_TOO_LONG"
        )
        String city,

        @Schema(description = "Customer province of residence.")
        @Pattern(
                regexp = ".*\\S.*",
                message = "PROVINCE_REQUIRED"
        )
        @Size(
                max = 100,
                message = "PROVINCE_TOO_LONG"
        )
        String province
) {
}
