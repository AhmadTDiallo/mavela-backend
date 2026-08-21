package com.mavela.backend.qswitch;

/**
 * Stable, non-sensitive integration failures. These are deliberately kept
 * separate from customer-facing API error codes until a customer endpoint and
 * its error contract exist.
 */
public enum QSwitchIntegrationErrorCode {
    INTEGRATION_UNAVAILABLE("The account provider is unavailable."),
    AUTHENTICATION_FAILED("The account provider authentication failed."),
    RATE_LIMITED("The account provider is temporarily rate limited."),
    TIMEOUT("The account provider did not respond in time."),
    PROVIDER_UNAVAILABLE("The account provider is temporarily unavailable."),
    INVALID_RESPONSE("The account provider returned an invalid response."),
    ACCOUNT_NOT_FOUND("The requested provider account was not found.");

    private final String safeMessage;

    QSwitchIntegrationErrorCode(String safeMessage) {
        this.safeMessage = safeMessage;
    }

    public String getSafeMessage() {
        return safeMessage;
    }
}
