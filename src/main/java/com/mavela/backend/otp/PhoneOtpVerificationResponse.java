package com.mavela.backend.otp;

import com.mavela.backend.customer.CustomerStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record PhoneOtpVerificationResponse(
        @Schema(description = "Identifier of the verified customer.")
        UUID customerId,

        @Schema(description = "Timestamp when the phone number was verified.")
        Instant phoneVerifiedAt,

        @Schema(description = "Customer lifecycle status after verification.")
        CustomerStatus status,

        @Schema(
                description = "Short-lived token required to select a username and create a PIN. Treat this value as sensitive.",
                accessMode = Schema.AccessMode.READ_ONLY
        )
        String pinSetupToken,

        @Schema(description = "Timestamp when the PIN setup token expires.")
        Instant pinSetupTokenExpiresAt
) {
}
