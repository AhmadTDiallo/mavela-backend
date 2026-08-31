package com.mavela.backend.admin.services;

import com.mavela.backend.services.catalogue.ServiceAvailability;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Explicitly limited to non-live presentation configuration. No provider,
 * product, payment, account, or funding field can be supplied here.
 */
public record UpdateServiceCatalogueRequest(
        @NotNull ServiceAvailability availability,
        @NotNull Boolean customerVisible,
        @NotNull @Max(100) Integer displayOrder,
        @NotNull ServiceCatalogueChangeReasonCode reasonCode,
        @NotBlank @Size(max = 500) String internalNote,
        @NotBlank @Size(max = 160)
        @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._:-]{7,159}")
        String idempotencyKey
) {
}
