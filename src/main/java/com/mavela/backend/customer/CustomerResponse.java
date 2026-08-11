package com.mavela.backend.customer;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
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
                customer.getPhoneVerifiedAt(),
                customer.getStatus(),
                customer.getKycStatus(),
                customer.getCreatedAt(),
                customer.getUpdatedAt()
        );
    }
}
