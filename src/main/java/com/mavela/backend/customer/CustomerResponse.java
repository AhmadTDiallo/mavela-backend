package com.mavela.backend.customer;

import java.time.Instant;
import java.util.UUID;

public record CustomerResponse(
        UUID id,
        String username,
        String handle,
        String phoneNumber,
        String email,
        String firstName,
        String lastName,
        String preferredLocale,
        Instant phoneVerifiedAt,
        CustomerStatus status,
        KycStatus kycStatus,
        Instant createdAt,
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