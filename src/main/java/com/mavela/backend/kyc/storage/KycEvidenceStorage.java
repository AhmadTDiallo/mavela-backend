package com.mavela.backend.kyc.storage;

import java.net.URI;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Map;

/**
 * Private object-storage port for KYC evidence. Implementations must never
 * make evidence public or return a persistent customer-visible object URL.
 */
public interface KycEvidenceStorage {

    UploadSession requestUpload(UploadRequest request);

    VerifiedObject verifyUpload(VerificationRequest request);

    /**
     * Opens a private, server-side read stream for evidence already known to
     * the application. Implementations must not create a download URL or
     * otherwise reveal storage-provider details to callers.
     *
     * <p>The caller owns the returned stream and must close it after the HTTP
     * response has been written.</p>
     */
    EvidenceStream openRead(ReadRequest request);

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

    record ReadRequest(
            String storageKey,
            String expectedMimeType,
            long expectedByteSize
    ) {
    }

    record EvidenceStream(
            String mimeType,
            long byteSize,
            InputStream inputStream
    ) implements AutoCloseable {

        @Override
        public void close() throws IOException {
            inputStream.close();
        }
    }
}
