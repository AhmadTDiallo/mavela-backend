package com.mavela.backend.kyc.storage;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Explicit opt-in smoke check for a developer's configured S3-compatible
 * store. It uses only a generated image and deletes the temporary object. It
 * is skipped unless MAVELA_KYC_STORAGE_SMOKE_TEST=true is present.
 */
class S3KycEvidenceStorageLocalSmokeTests {

    @Test
    void acceptsAndVerifiesPresignedPrivateEvidenceUpload() throws Exception {
        assumeTrue("true".equalsIgnoreCase(System.getenv(
                "MAVELA_KYC_STORAGE_SMOKE_TEST"
        )));

        byte[] image = syntheticPng();
        String checksum = sha256(image);
        String storageKey = "smoke-tests/" + UUID.randomUUID() + ".png";

        try (S3KycEvidenceStorage storage = new S3KycEvidenceStorage(
                properties()
        )) {
            KycEvidenceStorage.UploadSession session = storage.requestUpload(
                    new KycEvidenceStorage.UploadRequest(
                            storageKey,
                            "image/png",
                            image.length,
                            checksum
                    )
            );

            HttpRequest.Builder request = HttpRequest.newBuilder(
                    session.uploadUrl()
            ).PUT(HttpRequest.BodyPublishers.ofByteArray(image));
            session.requiredHeaders().forEach((name, value) -> {
                // HttpClient derives the signed content length from the body.
                if (!"content-length".equalsIgnoreCase(name)) {
                    request.header(name, value);
                }
            });

            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    request.build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            assertEquals(
                    200,
                    response.statusCode(),
                    "Local object storage rejected the signed upload: "
                            + storageErrorCode(response.body())
            );

            KycEvidenceStorage.VerifiedObject verified = storage.verifyUpload(
                    new KycEvidenceStorage.VerificationRequest(
                            storageKey,
                            "image/png",
                            image.length,
                            checksum
                    )
            );
            assertEquals("image/png", verified.mimeType());
            assertEquals(image.length, verified.byteSize());
        } finally {
            // The storage implementation is recreated so cleanup still occurs
            // if a PUT or verification assertion fails.
            try (S3KycEvidenceStorage storage = new S3KycEvidenceStorage(
                    properties()
            )) {
                storage.delete(storageKey);
            }
        }
    }

    private KycEvidenceStorageProperties properties() {
        KycEvidenceStorageProperties properties =
                new KycEvidenceStorageProperties();
        properties.setBucket(requiredEnvironment("MAVELA_KYC_STORAGE_BUCKET"));
        properties.setRegion(environmentOrDefault(
                "MAVELA_KYC_STORAGE_REGION",
                "af-south-1"
        ));
        properties.setEndpoint(URI.create(requiredEnvironment(
                "MAVELA_KYC_STORAGE_ENDPOINT"
        )));
        properties.setPresignEndpoint(URI.create(environmentOrDefault(
                "MAVELA_KYC_STORAGE_SMOKE_PRESIGN_ENDPOINT",
                requiredEnvironment("MAVELA_KYC_STORAGE_ENDPOINT")
        )));
        properties.setAccessKeyId(requiredEnvironment(
                "MAVELA_KYC_STORAGE_ACCESS_KEY_ID"
        ));
        properties.setSecretAccessKey(requiredEnvironment(
                "MAVELA_KYC_STORAGE_SECRET_ACCESS_KEY"
        ));
        properties.setForcePathStyle(true);
        properties.setAllowInsecureEndpoint(true);
        properties.setUploadUrlTtl(Duration.ofMinutes(1));
        String configuredEncryption = System.getenv(
                "MAVELA_KYC_STORAGE_SERVER_SIDE_ENCRYPTION"
        );
        properties.setServerSideEncryption(configuredEncryption == null
                ? "AES256"
                : configuredEncryption.trim());
        return properties;
    }

    private String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for the local storage smoke test.");
        }
        return value.trim();
    }

    private String environmentOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private byte[] syntheticPng() throws Exception {
        BufferedImage image = new BufferedImage(
                2,
                2,
                BufferedImage.TYPE_INT_RGB
        );
        image.setRGB(0, 0, Color.MAGENTA.getRGB());
        image.setRGB(1, 1, Color.DARK_GRAY.getRGB());

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        }
    }

    private String sha256(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder result = new StringBuilder(digest.length * 2);
        for (byte current : digest) {
            result.append(String.format(Locale.ROOT, "%02x", current));
        }
        return result.toString();
    }

    private String storageErrorCode(String responseBody) {
        if (responseBody == null) {
            return "unknown";
        }
        return responseElement(responseBody, "Code") + ": "
                + responseElement(responseBody, "Message");
    }

    private String responseElement(String responseBody, String element) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("<" + element + ">([^<]+)</" + element + ">")
                .matcher(responseBody);
        return matcher.find() ? matcher.group(1) : "unknown";
    }
}
