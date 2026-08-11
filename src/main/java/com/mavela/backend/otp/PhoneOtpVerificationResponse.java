package com.mavela.backend.otp;

import com.mavela.backend.customer.CustomerStatus;

import java.time.Instant;
import java.util.UUID;

public record PhoneOtpVerificationResponse(
        UUID customerId,
        Instant phoneVerifiedAt,
        CustomerStatus status,
        String pinSetupToken,
        Instant pinSetupTokenExpiresAt
) {
}