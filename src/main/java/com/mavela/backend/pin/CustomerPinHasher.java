package com.mavela.backend.pin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.UUID;

@Component
public class CustomerPinHasher {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final PasswordEncoder passwordEncoder;
    private final byte[] pepper;

    public CustomerPinHasher(
            @Value("${MAVELA_PIN_PEPPER:}")
            String configuredPepper
    ) {
        if (configuredPepper == null
                || configuredPepper
                .getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "MAVELA_PIN_PEPPER must contain at least 32 characters."
            );
        }

        this.pepper = configuredPepper
                .getBytes(StandardCharsets.UTF_8)
                .clone();

        this.passwordEncoder = new Argon2PasswordEncoder(
                16,
                32,
                1,
                19 * 1024,
                2
        );
    }

    public String hash(UUID customerId, String pin) {
        return passwordEncoder.encode(
                applyPepper(customerId, pin)
        );
    }

    public boolean matches(
            UUID customerId,
            String providedPin,
            String storedHash
    ) {
        return passwordEncoder.matches(
                applyPepper(customerId, providedPin),
                storedHash
        );
    }

    private String applyPepper(
            UUID customerId,
            String pin
    ) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);

            mac.init(new SecretKeySpec(
                    pepper,
                    HMAC_ALGORITHM
            ));

            String value = customerId + ":" + pin;

            byte[] result = mac.doFinal(
                    value.getBytes(StandardCharsets.UTF_8)
            );

            return Base64.getEncoder()
                    .encodeToString(result);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "Unable to protect the customer PIN.",
                    exception
            );
        }
    }
}