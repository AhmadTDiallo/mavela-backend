package com.mavela.backend.qswitch;

import java.time.Duration;

/**
 * Does not expose a provider response, URL, credential, or token in the
 * exception message. Callers may use the stable code for safe handling.
 */
public class QSwitchIntegrationException extends RuntimeException {

    private final QSwitchIntegrationErrorCode errorCode;
    private final Duration retryAfter;

    public QSwitchIntegrationException(QSwitchIntegrationErrorCode errorCode) {
        this(errorCode, null, null);
    }

    public QSwitchIntegrationException(QSwitchIntegrationErrorCode errorCode, Throwable cause) {
        this(errorCode, null, cause);
    }

    public QSwitchIntegrationException(QSwitchIntegrationErrorCode errorCode, Duration retryAfter) {
        this(errorCode, retryAfter, null);
    }

    private QSwitchIntegrationException(
            QSwitchIntegrationErrorCode errorCode,
            Duration retryAfter,
            Throwable cause
    ) {
        super(errorCode.getSafeMessage(), cause);
        this.errorCode = errorCode;
        this.retryAfter = retryAfter;
    }

    public QSwitchIntegrationErrorCode getErrorCode() {
        return errorCode;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
