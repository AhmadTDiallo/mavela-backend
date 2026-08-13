package com.mavela.backend.kyc;

import io.swagger.v3.oas.annotations.media.Schema;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Short-lived upload instructions. The URL is intentionally not persisted in
 * the database or included in later KYC application responses.
 */
public record KycEvidenceUploadSessionResponse(

        @Schema(description = "Identifier used to complete or remove this evidence.")
        UUID evidenceId,

        @Schema(description = "Private, short-lived URL for a direct HTTP PUT upload.")
        URI uploadUrl,

        @Schema(description = "Headers that must be supplied with the upload.")
        Map<String, String> requiredHeaders,

        @Schema(description = "Timestamp after which the upload URL cannot be used.")
        Instant expiresAt,

        @Schema(description = "Maximum accepted image size in bytes.")
        long maximumSizeBytes
) {
}
