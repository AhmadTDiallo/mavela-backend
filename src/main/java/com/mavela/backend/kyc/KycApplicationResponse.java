package com.mavela.backend.kyc;

import com.mavela.backend.customer.KycStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record KycApplicationResponse(
        @Schema(description = "Unique KYC application identifier.")
        UUID id,

        @Schema(description = "Sequential KYC attempt number for the current customer.")
        int attemptNumber,

        @Schema(description = "Current KYC application workflow status.")
        KycStatus status,

        @Schema(description = "Current resumable KYC draft step.")
        KycDraftStep currentStep,

        @Schema(description = "Selected identity document type when supplied.")
        KycDocumentType documentType,

        @Schema(description = "Timestamp when the customer started this KYC attempt.")
        Instant startedAt,

        @Schema(description = "Timestamp when draft progress was last saved.")
        Instant lastSavedAt,

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
        String rejectionReason,

        @Schema(description = "Customer-safe correction instructions when resubmission is required.")
        KycResubmissionResponse resubmission,

        @Schema(description = "Customer-safe metadata for draft evidence items.")
        List<KycEvidenceResponse> documents
) {

    public static KycApplicationResponse from(KycApplication application) {
        return from(application, null);
    }

    public static KycApplicationResponse from(
            KycApplication application,
            KycResubmissionResponse resubmission
    ) {
        return new KycApplicationResponse(
                application.getId(),
                application.getAttemptNumber(),
                application.getStatus(),
                application.getCurrentStep(),
                application.getDocumentType(),
                application.getStartedAt(),
                application.getLastSavedAt(),
                application.getCreatedAt(),
                application.getUpdatedAt(),
                application.getSubmittedAt(),
                application.getReviewStartedAt(),
                application.getDecidedAt(),
                application.getRejectionReason(),
                resubmission,
                application.getDocuments()
                        .stream()
                        .filter(KycDocument::isActive)
                        .map(KycEvidenceResponse::from)
                        .toList()
        );
    }
}
