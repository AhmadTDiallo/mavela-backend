package com.mavela.backend.kyc.review;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ApproveKycApplicationRequest(
        @NotNull(message = "KYC_STALE_APPLICATION_VERSION")
        @PositiveOrZero(message = "KYC_STALE_APPLICATION_VERSION")
        @Schema(description = "Version returned by the most recent detail response.", example = "3")
        Long expectedVersion,

        @Size(max = 2000, message = "FIELD_INVALID")
        @Schema(description = "Staff-only review note. Never returned to customer APIs.")
        String internalNotes
) {
}
