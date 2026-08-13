package com.mavela.backend.kyc;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * Customer-safe evidence metadata. Object keys, checksums, filenames, and
 * storage URLs intentionally never leave the service.
 */
public record KycEvidenceResponse(

        @Schema(description = "Identifier for this customer-owned evidence item.")
        UUID id,

        KycEvidenceType evidenceType,
        KycDocumentType documentType,
        KycDocumentSide documentSide,
        KycCaptureMethod captureMethod,
        String mimeType,
        long byteSize,
        KycEvidenceUploadStatus status,
        Instant createdAt,
        Instant uploadedAt
) {

    public static KycEvidenceResponse from(KycDocument document) {
        return new KycEvidenceResponse(
                document.getId(),
                document.getEvidenceType(),
                document.getDocumentType(),
                document.getDocumentSide(),
                document.getCaptureMethod(),
                document.getMimeType(),
                document.getFileSize(),
                document.getUploadStatus(),
                document.getCreatedAt(),
                document.getUploadedAt()
        );
    }
}
