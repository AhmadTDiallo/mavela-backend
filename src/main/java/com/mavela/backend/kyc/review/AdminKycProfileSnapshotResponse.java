package com.mavela.backend.kyc.review;

import com.mavela.backend.customer.Gender;
import com.mavela.backend.kyc.KycApplication;

import java.time.LocalDate;

/** Immutable profile values captured when the customer submitted KYC. */
public record AdminKycProfileSnapshotResponse(
        String firstName,
        String lastName,
        String preferredLocale,
        LocalDate dateOfBirth,
        String nationality,
        Gender gender,
        String addressLine,
        String city,
        String province
) {

    public static AdminKycProfileSnapshotResponse from(
            KycApplication application
    ) {
        return new AdminKycProfileSnapshotResponse(
                application.getProfileFirstName(),
                application.getProfileLastName(),
                application.getProfilePreferredLocale(),
                application.getProfileDateOfBirth(),
                application.getProfileNationality(),
                application.getProfileGender(),
                application.getProfileAddressLine(),
                application.getProfileCity(),
                application.getProfileProvince()
        );
    }
}
