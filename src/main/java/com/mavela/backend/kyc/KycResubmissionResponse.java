package com.mavela.backend.kyc;

import com.mavela.backend.kyc.review.KycMissingRequirement;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Narrow, customer-safe resubmission instruction set. It intentionally omits
 * staff identity, review notes, reason codes, audit metadata, storage details,
 * and evidence identifiers.
 */
public record KycResubmissionResponse(
        @Schema(
                description = "Customer-safe message supplied by the reviewer.",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String customerMessage,

        @Schema(
                description = "Only the customer corrections required before resubmission.",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        List<KycMissingRequirement> requiredCorrections,

        @Schema(
                description = "Required corrections the backend has confirmed since the resubmission request.",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        List<KycMissingRequirement> completedCorrections
) {
}
