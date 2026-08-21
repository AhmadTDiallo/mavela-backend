package com.mavela.backend.qswitch;

import java.time.Instant;

/** In-memory only cached token. Do not persist this type. */
record QSwitchOAuthAccessToken(String value, Instant refreshAt) {

    boolean isUsableAt(Instant instant) {
        return instant.isBefore(refreshAt);
    }

    @Override
    public String toString() {
        return "QSwitchOAuthAccessToken[redacted]";
    }
}
