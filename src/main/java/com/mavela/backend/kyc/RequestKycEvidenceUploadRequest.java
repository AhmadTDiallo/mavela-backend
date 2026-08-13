package com.mavela.backend.kyc;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * Metadata used to create a short-lived, private evidence upload session.
 * Image bytes never pass through this JSON API.
 */
public record RequestKycEvidenceUploadRequest(

        @NotNull(message = "FIELD_INVALID")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        KycEvidenceType evidenceType,

        @Schema(description = "Required for document evidence and omitted for a selfie.")
        KycDocumentType documentType,

        @NotNull(message = "FIELD_INVALID")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        KycDocumentSide documentSide,

        @NotNull(message = "FIELD_INVALID")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        KycCaptureMethod captureMethod,

        @NotBlank(message = "FIELD_INVALID")
        @Pattern(
                regexp = "(?i)^image/(?:jpeg|png)$",
                message = "FIELD_INVALID"
        )
        @Schema(
                description = "Declared image media type. Only JPEG and PNG are accepted.",
                example = "image/jpeg",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String mimeType,

        @Positive(message = "FIELD_INVALID")
        @Schema(
                description = "Declared image size in bytes.",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        long byteSize,

        @NotBlank(message = "FIELD_INVALID")
        @Pattern(
                regexp = "(?i)^[a-f0-9]{64}$",
                message = "FIELD_INVALID"
        )
        @Schema(
                description = "SHA-256 checksum of the exact image bytes.",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String sha256
) {
}
