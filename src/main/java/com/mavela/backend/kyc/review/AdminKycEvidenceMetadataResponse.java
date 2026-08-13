package com.mavela.backend.kyc.review;

import com.mavela.backend.kyc.KycCaptureMethod;
import com.mavela.backend.kyc.KycDocument;
import com.mavela.backend.kyc.KycDocumentSide;
import com.mavela.backend.kyc.KycDocumentType;
import com.mavela.backend.kyc.KycEvidenceType;
import com.mavela.backend.kyc.KycEvidenceUploadStatus;

import java.time.Instant;
import java.util.UUID;

/** Staff-facing metadata deliberately excluding keys, checksums, and URLs. */
public record AdminKycEvidenceMetadataResponse(
        UUID id,
        KycEvidenceType evidenceType,
        KycDocumentType documentType,
        KycDocumentSide documentSide,
        KycCaptureMethod captureMethod,
        String mimeType,
        long byteSize,
        KycEvidenceUploadStatus uploadStatus,
        Instant createdAt,
        Instant uploadedAt
) {

    public static AdminKycEvidenceMetadataResponse from(KycDocument document) {
        return new AdminKycEvidenceMetadataResponse(
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
