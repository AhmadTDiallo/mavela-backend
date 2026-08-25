package com.mavela.backend.admin.rewards;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateRewardsReversalRequest(
        @NotNull RewardsReversalReasonCode reasonCode,
        @NotBlank @Size(max = 500) String internalNote,
        @NotBlank @Size(max = 160)
        @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._:-]{7,159}")
        String idempotencyKey
) {
}
