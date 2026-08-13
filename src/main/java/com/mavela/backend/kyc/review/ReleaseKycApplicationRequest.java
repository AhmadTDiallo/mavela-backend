package com.mavela.backend.kyc.review;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ReleaseKycApplicationRequest(
        @NotNull(message = "KYC_STALE_APPLICATION_VERSION")
        @PositiveOrZero(message = "KYC_STALE_APPLICATION_VERSION")
        Long expectedVersion
) {
}
