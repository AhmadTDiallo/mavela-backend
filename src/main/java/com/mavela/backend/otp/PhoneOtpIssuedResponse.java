package com.mavela.backend.otp;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record PhoneOtpIssuedResponse(
        @Schema(description = "Identifier of the newly created verification challenge.")
        UUID challengeId,

        @Schema(
                description = "Masked phone-number destination for the verification code."
        )
        String destination,

        @Schema(description = "Timestamp when the verification challenge expires.")
        Instant expiresAt,

        @Schema(
                description = "Timestamp when another verification code may be requested."
        )
        Instant resendAvailableAt
) {
}
