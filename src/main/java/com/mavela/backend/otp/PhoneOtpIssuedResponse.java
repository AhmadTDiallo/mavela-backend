package com.mavela.backend.otp;

import java.time.Instant;
import java.util.UUID;

public record PhoneOtpIssuedResponse(
        UUID challengeId,
        String destination,
        Instant expiresAt,
        Instant resendAvailableAt
) {
}