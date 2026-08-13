package com.mavela.backend.kyc.support;

import com.mavela.backend.kyc.storage.KycEvidenceStorage;
import com.mavela.backend.kyc.storage.KycEvidenceStorageException;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InMemoryKycEvidenceStorageTests {

    @Test
    void opensValidatedImageThroughAPrivateServerSideStream()
            throws IOException {
        InMemoryKycEvidenceStorage storage = new InMemoryKycEvidenceStorage();
        byte[] image = png();
        storage.put("internal/private/evidence.png", "image/png", image);

        try (KycEvidenceStorage.EvidenceStream stream = storage.openRead(
                new KycEvidenceStorage.ReadRequest(
                        "internal/private/evidence.png",
                        "image/png",
                        image.length
                )
        )) {
            assertEquals("image/png", stream.mimeType());
            assertEquals(image.length, stream.byteSize());
            assertArrayEquals(image, stream.inputStream().readAllBytes());
        }
    }

    @Test
    void rejectsReadWhenTheExpectedMetadataDoesNotMatch() throws IOException {
        InMemoryKycEvidenceStorage storage = new InMemoryKycEvidenceStorage();
        byte[] image = png();
        storage.put("internal/private/evidence.png", "image/png", image);

        assertThrows(
                KycEvidenceStorageException.class,
                () -> storage.openRead(new KycEvidenceStorage.ReadRequest(
                        "internal/private/evidence.png",
                        "image/jpeg",
                        image.length
                ))
        );
    }

    private byte[] png() throws IOException {
        BufferedImage image = new BufferedImage(
                2,
                2,
                BufferedImage.TYPE_INT_RGB
        );
        image.setRGB(0, 0, Color.MAGENTA.getRGB());

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        }
    }
}
