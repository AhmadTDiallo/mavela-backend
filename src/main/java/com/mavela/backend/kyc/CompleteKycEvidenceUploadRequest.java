package com.mavela.backend.kyc;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Confirms that the exact object described by the upload session was sent to
 * private object storage.
 */
public record CompleteKycEvidenceUploadRequest(

        @NotBlank(message = "FIELD_INVALID")
        @Pattern(
                regexp = "(?i)^[a-f0-9]{64}$",
                message = "FIELD_INVALID"
        )
        @Schema(
                description = "SHA-256 checksum of the uploaded image.",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String sha256
) {
}
