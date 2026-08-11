package com.mavela.backend.otp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;

@Component
public class PhoneOtpHasher {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(PhoneOtpHasher.class);

    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] secret;

    public PhoneOtpHasher(
            @Value("${MAVELA_OTP_HMAC_SECRET:}")
            String configuredSecret
    ) {
        if (configuredSecret == null || configuredSecret.isBlank()) {
            byte[] generatedSecret = new byte[32];
            new SecureRandom().nextBytes(generatedSecret);
            secret = generatedSecret;

            LOGGER.warn(
                    "MAVELA_OTP_HMAC_SECRET is not configured. "
                            + "A temporary development secret was generated."
            );
            return;
        }

        byte[] configuredBytes =
                configuredSecret.getBytes(StandardCharsets.UTF_8);

        if (configuredBytes.length < 32) {
            throw new IllegalStateException(
                    "MAVELA_OTP_HMAC_SECRET must contain at least 32 characters."
            );
        }

        secret = configuredBytes.clone();
    }

    public String hash(UUID challengeId, String code) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));

            String value = challengeId + ":" + code;

            byte[] result = mac.doFinal(
                    value.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(result);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "Unable to hash the OTP code.",
                    exception
            );
        }
    }

    public boolean matches(
            UUID challengeId,
            String providedCode,
            String storedHash
    ) {
        String providedHash = hash(challengeId, providedCode);

        return MessageDigest.isEqual(
                providedHash.getBytes(StandardCharsets.UTF_8),
                storedHash.getBytes(StandardCharsets.UTF_8)
        );
    }
}