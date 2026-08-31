package com.mavela.backend.qswitch;

import java.time.Clock;
import java.time.Instant;

/**
 * Concurrency-safe, memory-only QSwitch staging token manager. Authentication
 * refreshes never replay a future payment or other state-changing command.
 */
public final class QSwitchStagingTokenManager {

    private final QSwitchProperties properties;
    private final QSwitchTokenTransport tokenTransport;
    private final Clock clock;
    private volatile QSwitchTokenPair cachedToken;

    public QSwitchStagingTokenManager(
            QSwitchProperties properties,
            QSwitchTokenTransport tokenTransport,
            Clock clock
    ) {
        this.properties = properties;
        this.tokenTransport = tokenTransport;
        this.clock = clock;
    }

    public String accessToken() {
        return validToken().accessToken();
    }

    public QSwitchAuthenticationStatus authenticationStatus() {
        if (!properties.isEnabled()) {
            return QSwitchAuthenticationStatus.DISABLED;
        }
        if (!properties.isStagingAuthenticationConfigured()) {
            return QSwitchAuthenticationStatus.NOT_CONFIGURED;
        }
        try {
            validToken();
            return QSwitchAuthenticationStatus.AUTHENTICATION_READY;
        } catch (QSwitchIntegrationException ignored) {
            return QSwitchAuthenticationStatus.AUTHENTICATION_FAILED;
        }
    }

    /** Clears only volatile memory after an authenticated provider read fails. */
    public void invalidate() {
        cachedToken = null;
    }

    private QSwitchTokenPair validToken() {
        if (!properties.isStagingAuthenticationConfigured()) {
            throw new QSwitchIntegrationException(
                    QSwitchIntegrationErrorCode.INTEGRATION_UNAVAILABLE
            );
        }

        Instant now = clock.instant();
        QSwitchTokenPair token = cachedToken;
        if (isUsable(token, now)) {
            return token;
        }

        synchronized (this) {
            now = clock.instant();
            token = cachedToken;
            if (isUsable(token, now)) {
                return token;
            }
            QSwitchTokenPair replacement = token == null
                    ? tokenTransport.acquireInitialToken(properties)
                    : refreshOrAcquireFresh(token);
            if (!isUsable(replacement, now)) {
                throw new QSwitchIntegrationException(
                        QSwitchIntegrationErrorCode.INVALID_RESPONSE
                );
            }
            cachedToken = replacement;
            return replacement;
        }
    }

    private QSwitchTokenPair refreshOrAcquireFresh(QSwitchTokenPair currentToken) {
        try {
            return tokenTransport.refreshToken(properties, currentToken);
        } catch (QSwitchIntegrationException ignored) {
            return tokenTransport.acquireInitialToken(properties);
        }
    }

    private boolean isUsable(QSwitchTokenPair token, Instant now) {
        return token != null && token.expiresAt().isAfter(
                now.plus(properties.getTokenRefreshSafetyWindow())
        );
    }
}
