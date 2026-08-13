package com.mavela.backend.kyc.review;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

public record RequestKycResubmissionRequest(
        @NotNull(message = "KYC_STALE_APPLICATION_VERSION")
        @PositiveOrZero(message = "KYC_STALE_APPLICATION_VERSION")
        Long expectedVersion,

        @NotNull(message = "KYC_REVIEW_REASON_REQUIRED")
        KycReviewReasonCode reasonCode,

        @NotBlank(message = "KYC_REVIEW_REASON_REQUIRED")
        @Size(max = 500, message = "FIELD_INVALID")
        String customerMessage,

        @Size(max = 2000, message = "FIELD_INVALID")
        String internalNotes,

        @Size(max = 8, message = "FIELD_INVALID")
        Set<UUID> evidenceIds,

        @Size(max = 5, message = "FIELD_INVALID")
        Set<KycMissingRequirement> missingRequirements
) {

    @AssertTrue(message = "KYC_REVIEW_REASON_REQUIRED")
    public boolean isAffectedEvidenceOrRequirementIdentified() {
        return (evidenceIds != null && !evidenceIds.isEmpty())
                || (missingRequirements != null && !missingRequirements.isEmpty());
    }
}
