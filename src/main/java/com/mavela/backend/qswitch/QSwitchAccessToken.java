package com.mavela.backend.qswitch;

import java.time.Instant;

/** In-memory-only OAuth token value. Never persist or log this record. */
public record QSwitchAccessToken(String value, Instant expiresAt) {

    public QSwitchAccessToken {
        if (value == null || value.isBlank() || expiresAt == null) {
            throw new IllegalArgumentException("token value and expiry are required");
        }
    }

    @Override
    public String toString() {
        return "QSwitchAccessToken[redacted]";
    }
}
