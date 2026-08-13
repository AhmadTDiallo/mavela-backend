package com.mavela.backend.kyc.storage;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;

class S3KycEvidenceStorageTests {

    @Test
    void preSignUsesTheClientReachableEndpoint() {
        KycEvidenceStorageProperties properties = newProperties();
        properties.setEndpoint(URI.create("http://127.0.0.1:9000"));
        properties.setPresignEndpoint(URI.create("http://10.0.2.2:9000"));

        try (S3KycEvidenceStorage storage = new S3KycEvidenceStorage(
                properties
        )) {
            KycEvidenceStorage.UploadSession session = storage.requestUpload(
                    new KycEvidenceStorage.UploadRequest(
                            "kyc/customer/application/document.jpg",
                            "image/jpeg",
                            3,
                            "a".repeat(64)
                    )
            );

            assertEquals("10.0.2.2", session.uploadUrl().getHost());
            assertEquals(9000, session.uploadUrl().getPort());
        }
    }

    private KycEvidenceStorageProperties newProperties() {
        KycEvidenceStorageProperties properties =
                new KycEvidenceStorageProperties();
        properties.setBucket("mavela-kyc-local");
        properties.setRegion("af-south-1");
        properties.setForcePathStyle(true);
        properties.setAllowInsecureEndpoint(true);
        properties.setAccessKeyId("local-access-key");
        properties.setSecretAccessKey("local-secret-key");
        return properties;
    }
}
