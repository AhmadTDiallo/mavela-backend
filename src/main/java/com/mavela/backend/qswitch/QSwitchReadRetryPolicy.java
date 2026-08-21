package com.mavela.backend.qswitch;

import java.time.Duration;

/**
 * Bounded policy for future idempotent account reads only. It must never be
 * reused for transfers or any state-changing provider command.
 */
public class QSwitchReadRetryPolicy {

    private final QSwitchProperties properties;

    public QSwitchReadRetryPolicy(QSwitchProperties properties) {
        this.properties = properties;
    }

    public boolean shouldRetry(QSwitchIntegrationErrorCode errorCode, int failedAttemptCount) {
        return failedAttemptCount < properties.getMaxReadRetries()
                && (errorCode == QSwitchIntegrationErrorCode.TIMEOUT
                || errorCode == QSwitchIntegrationErrorCode.RATE_LIMITED
                || errorCode == QSwitchIntegrationErrorCode.PROVIDER_UNAVAILABLE);
    }

    public Duration backoffFor(int failedAttemptCount, Duration retryAfter) {
        var cap = properties.getRetryMaxBackoff();
        if (retryAfter != null && !retryAfter.isNegative()) {
            return retryAfter.compareTo(cap) > 0 ? cap : retryAfter;
        }

        long multiplier = 1L << Math.min(Math.max(failedAttemptCount - 1, 0), 30);
        try {
            var candidate = properties.getRetryInitialBackoff().multipliedBy(multiplier);
            return candidate.compareTo(cap) > 0 ? cap : candidate;
        } catch (ArithmeticException exception) {
            return cap;
        }
    }
}
