package com.mavela.backend.kyc.storage;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

/**
 * Private object-storage port for KYC evidence. Implementations must never
 * make evidence public or return a persistent customer-visible object URL.
 */
public interface KycEvidenceStorage {

    UploadSession requestUpload(UploadRequest request);

    VerifiedObject verifyUpload(VerificationRequest request);

    void delete(String storageKey);

    record UploadRequest(
            String storageKey,
            String mimeType,
            long byteSize,
            String sha256Checksum
    ) {
    }

    record UploadSession(
            URI uploadUrl,
            Map<String, String> requiredHeaders,
            Instant expiresAt
    ) {
    }

    record VerificationRequest(
            String storageKey,
            String expectedMimeType,
            long expectedByteSize,
            String expectedSha256Checksum
    ) {
    }

    record VerifiedObject(
            String mimeType,
            long byteSize,
            String sha256Checksum
    ) {
    }
}
