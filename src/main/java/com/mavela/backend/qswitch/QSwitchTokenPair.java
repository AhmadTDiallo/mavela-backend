package com.mavela.backend.qswitch;

import java.time.Instant;

/** In-memory-only token pair. Its values must never be persisted or logged. */
public record QSwitchTokenPair(
        String accessToken,
        String refreshToken,
        Instant expiresAt
) {

    public QSwitchTokenPair {
        if (accessToken == null || accessToken.isBlank()
                || refreshToken == null || refreshToken.isBlank()
                || expiresAt == null) {
            throw new IllegalArgumentException(
                    "access token, refresh token, and expiry are required"
            );
        }
    }

    @Override
    public String toString() {
        return "QSwitchTokenPair[redacted]";
    }
}
