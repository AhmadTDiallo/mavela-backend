package com.mavela.backend.customer;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CustomerResponse(
        @Schema(description = "Unique customer identifier.")
        UUID id,

        @Schema(
                description = "Customer username, available after username selection."
        )
        String username,

        @Schema(
                description = "Customer-facing handle composed from the username with a dollar-sign prefix."
        )
        String handle,

        @Schema(
                description = "Customer phone number in international E.164 format."
        )
        String phoneNumber,

        @Schema(description = "Customer email address when supplied.")
        String email,

        @Schema(description = "Customer given name.")
        String firstName,

        @Schema(description = "Customer family name.")
        String lastName,

        @Schema(description = "Preferred language for customer communications.")
        String preferredLocale,

        @Schema(description = "Customer date of birth when supplied.")
        LocalDate dateOfBirth,

        @Schema(description = "Customer nationality code when supplied.")
        String nationality,

        @Schema(description = "Customer gender when supplied.")
        Gender gender,

        @Schema(description = "Customer residential address when supplied.")
        String addressLine,

        @Schema(description = "Customer city of residence when supplied.")
        String city,

        @Schema(description = "Customer province of residence when supplied.")
        String province,

        @Schema(description = "Whether the customer has completed all profile fields.")
        boolean profileComplete,

        @Schema(description = "Timestamp when the customer profile was first completed.")
        Instant profileCompletedAt,

        @Schema(
                description = "Timestamp when the customer's phone number was verified."
        )
        Instant phoneVerifiedAt,

        @Schema(description = "Current customer lifecycle status.")
        CustomerStatus status,

        @Schema(description = "Current know-your-customer verification status.")
        KycStatus kycStatus,

        @Schema(description = "Timestamp when the customer was created.")
        Instant createdAt,

        @Schema(description = "Timestamp when the customer was last updated.")
        Instant updatedAt
) {

    public static CustomerResponse from(Customer customer) {
        String username = customer.getUsername();

        return new CustomerResponse(
                customer.getId(),
                username,
                username == null ? null : "$" + username,
                customer.getPhoneNumber(),
                customer.getEmail(),
                customer.getFirstName(),
                customer.getLastName(),
                customer.getPreferredLocale(),
                customer.getDateOfBirth(),
                customer.getNationality(),
                customer.getGender(),
                customer.getAddressLine(),
                customer.getCity(),
                customer.getProvince(),
                customer.getProfileCompletedAt() != null,
                customer.getProfileCompletedAt(),
                customer.getPhoneVerifiedAt(),
                customer.getStatus(),
                customer.getKycStatus(),
                customer.getCreatedAt(),
                customer.getUpdatedAt()
        );
    }
}
