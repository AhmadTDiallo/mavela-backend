package com.mavela.backend.pin;

import com.mavela.backend.customer.Customer;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Component
public class PinSetupTokenManager {

    private static final Duration TOKEN_LIFETIME =
            Duration.ofMinutes(10);

    private static final int TOKEN_BYTE_LENGTH = 32;

    private final PinSetupTokenRepository tokenRepository;
    private final SecureRandom secureRandom;

    public PinSetupTokenManager(
            PinSetupTokenRepository tokenRepository
    ) {
        this.tokenRepository = tokenRepository;
        this.secureRandom = new SecureRandom();
    }

    public IssuedPinSetupToken issue(
            Customer customer,
            Instant now
    ) {
        tokenRepository
                .findFirstByCustomer_IdAndConsumedAtIsNullAndInvalidatedAtIsNullOrderByCreatedAtDesc(
                        customer.getId()
                )
                .ifPresent(existingToken ->
                        existingToken.invalidate(now)
                );

        tokenRepository.flush();

        byte[] tokenBytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(tokenBytes);

        String rawToken = Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(tokenBytes);

        Instant expiresAt = now.plus(TOKEN_LIFETIME);

        PinSetupToken token = new PinSetupToken(
                UUID.randomUUID(),
                customer,
                hash(rawToken),
                expiresAt,
                now
        );

        tokenRepository.saveAndFlush(token);

        return new IssuedPinSetupToken(
                rawToken,
                expiresAt
        );
    }

    public String hash(String rawToken) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] result = digest.digest(
                    rawToken.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(result);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable.",
                    exception
            );
        }
    }

    public boolean matches(
            String rawToken,
            String storedTokenHash
    ) {
        byte[] calculatedHash = hash(rawToken)
                .getBytes(StandardCharsets.US_ASCII);

        byte[] expectedHash = storedTokenHash
                .getBytes(StandardCharsets.US_ASCII);

        return MessageDigest.isEqual(
                calculatedHash,
                expectedHash
        );
    }

    public record IssuedPinSetupToken(
            String token,
            Instant expiresAt
    ) {
    }
}