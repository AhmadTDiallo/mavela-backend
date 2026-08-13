package com.mavela.backend.kyc;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Customer-owned, non-sensitive progress for an editable KYC application.
 */
public record UpdateKycApplicationDraftRequest(

        @NotNull(message = "FIELD_INVALID")
        @Schema(
                description = "The customer-visible KYC step to resume.",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        KycDraftStep currentStep,

        @Schema(
                description = "Identity document selected for this application."
        )
        KycDocumentType documentType
) {
}
