package com.mavela.backend.kyc.review;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record RejectKycApplicationRequest(
        @NotNull(message = "KYC_STALE_APPLICATION_VERSION")
        @PositiveOrZero(message = "KYC_STALE_APPLICATION_VERSION")
        Long expectedVersion,

        @NotNull(message = "KYC_REVIEW_REASON_REQUIRED")
        KycReviewReasonCode reasonCode,

        @NotBlank(message = "KYC_REVIEW_REASON_REQUIRED")
        @Size(max = 500, message = "FIELD_INVALID")
        String customerMessage,

        @Size(max = 2000, message = "FIELD_INVALID")
        String internalNotes
) {
}
