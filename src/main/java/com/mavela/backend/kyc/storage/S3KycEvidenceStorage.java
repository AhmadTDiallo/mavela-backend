package com.mavela.backend.kyc.storage;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Production S3/S3-compatible private evidence store. The class uses
 * short-lived PUT URLs and validates the retrieved object before an item can
 * become usable by a KYC application.
 */
final class S3KycEvidenceStorage implements KycEvidenceStorage, AutoCloseable {

    private static final String LOCAL_ENCRYPTION_DISABLED = "NONE";

    private final KycEvidenceStorageProperties properties;
    private final S3Client client;
    private final S3Presigner presigner;

    S3KycEvidenceStorage(KycEvidenceStorageProperties properties) {
        this.properties = properties;
        validateProperties(properties);

        S3Configuration serviceConfiguration = S3Configuration.builder()
                .pathStyleAccessEnabled(properties.isForcePathStyle())
                .build();
        AwsCredentialsProvider credentialsProvider = credentialsProvider(
                properties
        );

        S3ClientBuilder clientBuilder = S3Client.builder()
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(credentialsProvider)
                .serviceConfiguration(serviceConfiguration);
        S3Presigner.Builder presignerBuilder = S3Presigner.builder()
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(credentialsProvider)
                .serviceConfiguration(serviceConfiguration);

        if (properties.getEndpoint() != null) {
            clientBuilder.endpointOverride(properties.getEndpoint());
        }
        URI presignEndpoint = properties.getPresignEndpoint() != null
                ? properties.getPresignEndpoint()
                : properties.getEndpoint();
        if (presignEndpoint != null) {
            presignerBuilder.endpointOverride(presignEndpoint);
        }

        client = clientBuilder.build();
        presigner = presignerBuilder.build();
    }

    @Override
    public UploadSession requestUpload(UploadRequest request) {
        validateExpectedObject(request.mimeType(), request.byteSize());

        try {
            PutObjectRequest.Builder putObjectBuilder = PutObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(request.storageKey())
                    .contentType(normalizeMimeType(request.mimeType()))
                    .contentLength(request.byteSize())
                    .checksumSHA256(checksumAsBase64(request.sha256Checksum()));
            if (hasConfiguredEncryption(properties)) {
                putObjectBuilder.serverSideEncryption(
                        ServerSideEncryption.fromValue(
                                properties.getServerSideEncryption().trim()
                        )
                );
            }
            PutObjectRequest putObject = putObjectBuilder.build();

            PresignedPutObjectRequest presigned = presigner.presignPutObject(
                    PutObjectPresignRequest.builder()
                            .signatureDuration(properties.getUploadUrlTtl())
                            .putObjectRequest(putObject)
                            .build()
            );

            Map<String, String> requiredHeaders = new LinkedHashMap<>();
            presigned.httpRequest().headers().forEach((name, values) -> {
                if (!"host".equalsIgnoreCase(name)) {
                    requiredHeaders.put(name, String.join(",", values));
                }
            });

            return new UploadSession(
                    presigned.url().toURI(),
                    Map.copyOf(requiredHeaders),
                    Instant.now().plus(properties.getUploadUrlTtl())
            );
        } catch (SdkException | java.net.URISyntaxException exception) {
            throw new KycEvidenceStorageException();
        }
    }

    @Override
    public VerifiedObject verifyUpload(VerificationRequest request) {
        validateExpectedObject(
                request.expectedMimeType(),
                request.expectedByteSize()
        );

        try {
            HeadObjectResponse head = client.headObject(
                    HeadObjectRequest.builder()
                            .bucket(properties.getBucket())
                            .key(request.storageKey())
                            .build()
            );

            String expectedMimeType = normalizeMimeType(
                    request.expectedMimeType()
            );
            if (!Objects.equals(
                    normalizeMimeType(head.contentType()),
                    expectedMimeType
            ) || head.contentLength() == null
                    || head.contentLength() != request.expectedByteSize()) {
                throw new KycEvidenceStorageException();
            }

            ResponseBytes<GetObjectResponse> response = client.getObjectAsBytes(
                    GetObjectRequest.builder()
                            .bucket(properties.getBucket())
                            .key(request.storageKey())
                            .build()
            );
            byte[] bytes = response.asByteArray();

            if (bytes.length != request.expectedByteSize()
                    || bytes.length > properties.getMaxImageSizeBytes()
                    || !matchesMagicBytes(bytes, expectedMimeType)
                    || !isDecodableImage(bytes)) {
                throw new KycEvidenceStorageException();
            }

            String actualChecksum = sha256(bytes);
            if (!actualChecksum.equalsIgnoreCase(
                    request.expectedSha256Checksum()
            )) {
                throw new KycEvidenceStorageException();
            }

            return new VerifiedObject(
                    expectedMimeType,
                    bytes.length,
                    actualChecksum
            );
        } catch (KycEvidenceStorageException exception) {
            throw exception;
        } catch (SdkException exception) {
            throw new KycEvidenceStorageException();
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(storageKey)
                    .build());
        } catch (SdkException exception) {
            throw new KycEvidenceStorageException();
        }
    }

    @Override
    public void close() {
        presigner.close();
        client.close();
    }

    private void validateProperties(KycEvidenceStorageProperties properties) {
        if (isBlank(properties.getBucket())
                || isBlank(properties.getRegion())
                || properties.getUploadUrlTtl() == null
                || properties.getUploadUrlTtl().isNegative()
                || properties.getUploadUrlTtl().isZero()
                || properties.getMaxImageSizeBytes() <= 0
                || properties.getMaxImageSizeBytes() > 10_485_760) {
            throw new IllegalStateException(
                    "Invalid KYC evidence storage configuration."
            );
        }

        validateSecureEndpoint(properties.getEndpoint(), properties);
        validateSecureEndpoint(properties.getPresignEndpoint(), properties);
        if (!hasConfiguredEncryption(properties)
                && !usesExplicitLocalInsecureEndpoint(properties)) {
            throw new IllegalStateException(
                    "KYC evidence storage encryption is required outside explicit local development."
            );
        }
    }

    private AwsCredentialsProvider credentialsProvider(
            KycEvidenceStorageProperties properties
    ) {
        boolean accessKeyConfigured = !isBlank(properties.getAccessKeyId());
        boolean secretConfigured = !isBlank(properties.getSecretAccessKey());
        if (accessKeyConfigured != secretConfigured) {
            throw new IllegalStateException(
                    "KYC storage access key and secret must be configured together."
            );
        }
        if (accessKeyConfigured) {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(
                    properties.getAccessKeyId().trim(),
                    properties.getSecretAccessKey().trim()
            ));
        }
        return DefaultCredentialsProvider.create();
    }

    private void validateSecureEndpoint(
            URI endpoint,
            KycEvidenceStorageProperties properties
    ) {
        if (endpoint != null
                && "http".equalsIgnoreCase(endpoint.getScheme())
                && !properties.isAllowInsecureEndpoint()) {
            throw new IllegalStateException(
                    "An insecure KYC storage endpoint is allowed only for explicit local development."
            );
        }
    }

    private boolean usesExplicitLocalInsecureEndpoint(
            KycEvidenceStorageProperties properties
    ) {
        return properties.isAllowInsecureEndpoint()
                && hasHttpEndpoint(properties.getEndpoint());
    }

    private boolean hasConfiguredEncryption(
            KycEvidenceStorageProperties properties
    ) {
        return !isBlank(properties.getServerSideEncryption())
                && !LOCAL_ENCRYPTION_DISABLED.equalsIgnoreCase(
                properties.getServerSideEncryption().trim()
        );
    }

    private boolean hasHttpEndpoint(URI endpoint) {
        return endpoint != null
                && "http".equalsIgnoreCase(endpoint.getScheme());
    }

    private void validateExpectedObject(String mimeType, long byteSize) {
        String normalizedMimeType = normalizeMimeType(mimeType);
        if ((!"image/jpeg".equals(normalizedMimeType)
                && !"image/png".equals(normalizedMimeType))
                || byteSize <= 0
                || byteSize > properties.getMaxImageSizeBytes()) {
            throw new KycEvidenceStorageException();
        }
    }

    private String normalizeMimeType(String value) {
        if (value == null) {
            return "";
        }

        return value.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private boolean matchesMagicBytes(byte[] bytes, String mimeType) {
        if ("image/jpeg".equals(mimeType)) {
            return bytes.length >= 3
                    && (bytes[0] & 0xff) == 0xff
                    && (bytes[1] & 0xff) == 0xd8
                    && (bytes[2] & 0xff) == 0xff;
        }

        return bytes.length >= 8
                && (bytes[0] & 0xff) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4e
                && bytes[3] == 0x47
                && bytes[4] == 0x0d
                && bytes[5] == 0x0a
                && bytes[6] == 0x1a
                && bytes[7] == 0x0a;
    }

    private boolean isDecodableImage(byte[] bytes) {
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
            BufferedImage image = ImageIO.read(input);
            return image != null && image.getWidth() > 0 && image.getHeight() > 0;
        } catch (Exception exception) {
            return false;
        }
    }

    private String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder value = new StringBuilder(digest.length * 2);
            for (byte current : digest) {
                value.append(String.format("%02x", current));
            }
            return value.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private String checksumAsBase64(String hexChecksum) {
        if (hexChecksum == null || !hexChecksum.matches("(?i)^[a-f0-9]{64}$")) {
            throw new KycEvidenceStorageException();
        }

        byte[] bytes = new byte[hexChecksum.length() / 2];
        for (int index = 0; index < hexChecksum.length(); index += 2) {
            bytes[index / 2] = (byte) Integer.parseInt(
                    hexChecksum.substring(index, index + 2),
                    16
            );
        }
        return Base64.getEncoder().encodeToString(bytes);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
