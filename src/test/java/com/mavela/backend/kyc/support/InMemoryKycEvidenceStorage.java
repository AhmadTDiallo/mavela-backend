package com.mavela.backend.kyc.support;

import com.mavela.backend.kyc.storage.KycEvidenceStorage;
import com.mavela.backend.kyc.storage.KycEvidenceStorageException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test-only private-storage substitute. It models direct object storage and
 * never belongs to the production application context.
 */
public final class InMemoryKycEvidenceStorage implements KycEvidenceStorage {

    private final Map<String, StoredObject> objects = new ConcurrentHashMap<>();

    @Override
    public UploadSession requestUpload(UploadRequest request) {
        return new UploadSession(
                URI.create("https://kyc-storage.test.invalid/upload/"
                        + request.storageKey()),
                Map.of("content-type", request.mimeType()),
                Instant.now().plusSeconds(600)
        );
    }

    @Override
    public VerifiedObject verifyUpload(VerificationRequest request) {
        StoredObject object = objects.get(request.storageKey());
        if (object == null
                || !object.mimeType().equals(normalizeMimeType(
                request.expectedMimeType()
        ))
                || object.bytes().length != request.expectedByteSize()
                || !matchesMagicBytes(object.bytes(), object.mimeType())
                || !isDecodableImage(object.bytes())
                || !sha256(object.bytes()).equalsIgnoreCase(
                request.expectedSha256Checksum()
        )) {
            throw new KycEvidenceStorageException();
        }

        return new VerifiedObject(
                object.mimeType(),
                object.bytes().length,
                sha256(object.bytes())
        );
    }

    @Override
    public EvidenceStream openRead(ReadRequest request) {
        StoredObject object = objects.get(request.storageKey());
        String expectedMimeType = normalizeMimeType(request.expectedMimeType());
        if (object == null
                || !object.mimeType().equals(expectedMimeType)
                || object.bytes().length != request.expectedByteSize()
                || !matchesMagicBytes(object.bytes(), expectedMimeType)
                || !isDecodableImage(object.bytes())) {
            throw new KycEvidenceStorageException();
        }

        InputStream stream = new ByteArrayInputStream(object.bytes().clone());
        return new EvidenceStream(
                expectedMimeType,
                object.bytes().length,
                stream
        );
    }

    @Override
    public void delete(String storageKey) {
        objects.remove(storageKey);
    }

    public void put(String storageKey, String mimeType, byte[] bytes) {
        objects.put(
                storageKey,
                new StoredObject(normalizeMimeType(mimeType), bytes.clone())
        );
    }

    private String normalizeMimeType(String mimeType) {
        return mimeType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte current : digest) {
                result.append(String.format("%02x", current));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
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

    private record StoredObject(String mimeType, byte[] bytes) {
    }
}
