package com.mavela.backend.qswitch;

import java.time.Clock;
import java.time.Instant;

/**
 * Thread-safe, in-memory-only access-token cache. Synchronization prevents a
 * refresh stampede when multiple read requests see the same expiring token.
 */
public class QSwitchOAuthTokenClient {

    private final QSwitchProperties properties;
    private final QSwitchTokenTransport tokenTransport;
    private final Clock clock;
    private volatile QSwitchAccessToken cachedToken;

    public QSwitchOAuthTokenClient(
            QSwitchProperties properties,
            QSwitchTokenTransport tokenTransport,
            Clock clock
    ) {
        this.properties = properties;
        this.tokenTransport = tokenTransport;
        this.clock = clock;
    }

    public String accessToken() {
        if (!properties.isLiveModeConfigured()) {
            throw new QSwitchIntegrationException(QSwitchIntegrationErrorCode.INTEGRATION_UNAVAILABLE);
        }

        var now = clock.instant();
        var token = cachedToken;
        if (isUsable(token, now)) {
            return token.value();
        }

        synchronized (this) {
            now = clock.instant();
            token = cachedToken;
            if (!isUsable(token, now)) {
                token = tokenTransport.requestToken(properties);
                if (!isUsable(token, now)) {
                    throw new QSwitchIntegrationException(QSwitchIntegrationErrorCode.INVALID_RESPONSE);
                }
                cachedToken = token;
            }
            return token.value();
        }
    }

    /** Clears only volatile memory after an authenticated provider read fails. */
    public void invalidate() {
        cachedToken = null;
    }

    private boolean isUsable(QSwitchAccessToken token, Instant now) {
        return token != null && token.expiresAt().isAfter(now.plus(properties.getTokenRefreshSafetyWindow()));
    }
}
