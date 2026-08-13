package com.mavela.backend.kyc.review;

import com.mavela.backend.customer.Customer;
import io.swagger.v3.oas.annotations.media.Schema;

/** Minimum profile information required for KYC queue triage. */
public record AdminCustomerTriageResponse(
        @Schema(description = "Customer full name for KYC triage.") String displayName,
        @Schema(description = "Mavela handle when selected.") String handle,
        @Schema(description = "Masked phone number for triage.") String maskedPhoneNumber
) {

    public static AdminCustomerTriageResponse from(Customer customer) {
        String username = customer.getUsername();
        return new AdminCustomerTriageResponse(
                customer.getFirstName() + " " + customer.getLastName(),
                username == null ? null : "$" + username,
                maskPhoneNumber(customer.getPhoneNumber())
        );
    }

    private static String maskPhoneNumber(String value) {
        if (value == null || value.length() <= 4) {
            return "••••";
        }

        return "••••" + value.substring(value.length() - 4);
    }
}
