package com.mavela.backend.qswitch;

import java.time.Instant;

/** In-memory-only QSwitch access-token cache entry. It must never be persisted or logged. */
public record QSwitchTokenPair(
        String accessToken,
        Instant expiresAt
) {

    public QSwitchTokenPair {
        if (accessToken == null || accessToken.isBlank() || expiresAt == null) {
            throw new IllegalArgumentException(
                    "access token and expiry are required"
            );
        }
    }

    @Override
    public String toString() {
        return "QSwitchTokenPair[redacted]";
    }
}
