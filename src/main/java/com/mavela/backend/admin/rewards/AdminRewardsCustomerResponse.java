package com.mavela.backend.admin.rewards;

import com.mavela.backend.customer.Customer;
import com.mavela.backend.customer.KycStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/** Minimal triage data; never a full customer profile. */
@Schema(description = "Minimal staff rewards customer triage record.")
public record AdminRewardsCustomerResponse(
        @Schema(description = "Opaque customer identifier for staff rewards routes.")
        UUID customerId,
        String displayName,
        String handle,
        String maskedPhoneNumber,
        KycStatus kycStatus,
        long availableGems,
        int currentStreakDays
) {

    static AdminRewardsCustomerResponse from(
            Customer customer,
            long availableGems,
            int currentStreakDays
    ) {
        return new AdminRewardsCustomerResponse(
                customer.getPublicId(),
                customer.getFirstName() + " " + customer.getLastName(),
                customer.getUsername() == null
                        ? null : "$" + customer.getUsername(),
                maskPhone(customer.getPhoneNumber()),
                customer.getKycStatus(),
                availableGems,
                currentStreakDays
        );
    }

    private static String maskPhone(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() <= 4) {
            return "****";
        }

        int suffixLength = Math.min(3, phoneNumber.length() - 1);
        int maskedLength = Math.max(4,
                phoneNumber.length() - suffixLength - 1);
        return phoneNumber.charAt(0)
                + "*".repeat(maskedLength)
                + phoneNumber.substring(phoneNumber.length() - suffixLength);
    }
}
