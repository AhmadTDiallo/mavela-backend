package com.mavela.backend.kyc;

import com.mavela.backend.customer.KycStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record KycApplicationResponse(
        @Schema(description = "Unique KYC application identifier.")
        UUID id,

        @Schema(description = "Sequential KYC attempt number for the current customer.")
        int attemptNumber,

        @Schema(description = "Current KYC application workflow status.")
        KycStatus status,

        @Schema(description = "Timestamp when the KYC application was created.")
        Instant createdAt,

        @Schema(description = "Timestamp when the KYC application was last updated.")
        Instant updatedAt,

        @Schema(description = "Timestamp when the KYC application was submitted.")
        Instant submittedAt,

        @Schema(description = "Timestamp when compliance review started.")
        Instant reviewStartedAt,

        @Schema(description = "Timestamp when a KYC decision was made.")
        Instant decidedAt,

        @Schema(description = "Non-sensitive rejection reason when a decision requires it.")
        String rejectionReason
) {

    public static KycApplicationResponse from(KycApplication application) {
        return new KycApplicationResponse(
                application.getId(),
                application.getAttemptNumber(),
                application.getStatus(),
                application.getCreatedAt(),
                application.getUpdatedAt(),
                application.getSubmittedAt(),
                application.getReviewStartedAt(),
                application.getDecidedAt(),
                application.getRejectionReason()
        );
    }
}
