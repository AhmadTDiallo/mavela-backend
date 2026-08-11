package com.mavela.backend.auth;

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
import java.util.Optional;
import java.util.UUID;

@Component
public class RefreshTokenService {

    private static final Duration REFRESH_TOKEN_TTL =
            Duration.ofDays(30);

    private static final int TOKEN_BYTE_LENGTH = 32;

    private final AuthRefreshTokenRepository tokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(
            AuthRefreshTokenRepository tokenRepository
    ) {
        this.tokenRepository = tokenRepository;
    }

    public Optional<AuthRefreshToken> findForUpdate(
            String rawToken
    ) {
        return tokenRepository.findByTokenHashForUpdate(
                hash(rawToken)
        );
    }

    public IssuedRefreshToken issue(
            Customer customer,
            Instant now
    ) {
        Instant expiresAt = now.plus(REFRESH_TOKEN_TTL);

        GeneratedRefreshToken generated =
                generate(
                        customer,
                        UUID.randomUUID(),
                        expiresAt,
                        now
                );

        tokenRepository.save(generated.entity());

        return generated.issuedToken();
    }

    public IssuedRefreshToken rotate(
            AuthRefreshToken currentToken,
            Instant now
    ) {
        /*
         * The replacement keeps the original family expiry.
         * Therefore, a session cannot be extended indefinitely
         * by repeatedly refreshing it.
         */
        GeneratedRefreshToken replacement =
                generate(
                        currentToken.getCustomer(),
                        currentToken.getFamilyId(),
                        currentToken.getExpiresAt(),
                        now
                );

        tokenRepository.saveAndFlush(
                replacement.entity()
        );

        currentToken.consume(
                now,
                replacement.entity().getId()
        );

        return replacement.issuedToken();
    }

    public int revokeFamily(
            UUID familyId,
            Instant now
    ) {
        return tokenRepository.revokeActiveFamily(
                familyId,
                now
        );
    }

    private GeneratedRefreshToken generate(
            Customer customer,
            UUID familyId,
            Instant expiresAt,
            Instant now
    ) {
        byte[] tokenBytes =
                new byte[TOKEN_BYTE_LENGTH];

        secureRandom.nextBytes(tokenBytes);

        String rawToken = Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(tokenBytes);

        AuthRefreshToken entity =
                new AuthRefreshToken(
                        customer,
                        familyId,
                        hash(rawToken),
                        expiresAt,
                        now
                );

        return new GeneratedRefreshToken(
                entity,
                new IssuedRefreshToken(
                        rawToken,
                        expiresAt
                )
        );
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] result = digest.digest(
                    rawToken.getBytes(
                            StandardCharsets.UTF_8
                    )
            );

            return HexFormat.of().formatHex(result);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable.",
                    exception
            );
        }
    }

    private record GeneratedRefreshToken(
            AuthRefreshToken entity,
            IssuedRefreshToken issuedToken
    ) {
    }

    public record IssuedRefreshToken(
            String value,
            Instant expiresAt
    ) {
    }
}