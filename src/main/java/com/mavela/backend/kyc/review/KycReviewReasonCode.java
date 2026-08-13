package com.mavela.backend.kyc.review;

/**
 * Structured reasons distinguish correctable customer actions from terminal
 * compliance decisions. New values require an explicit product/compliance
 * review rather than accepting arbitrary text from staff clients.
 */
public enum KycReviewReasonCode {
    DOCUMENT_UNREADABLE(false),
    DOCUMENT_EXPIRED(false),
    SELFIE_UNCLEAR(false),
    PROFILE_INFORMATION_MISMATCH(false),
    MISSING_REQUIRED_INFORMATION(false),
    IDENTITY_MISMATCH(true),
    DOCUMENT_FRAUD_SUSPECTED(true),
    DUPLICATE_IDENTITY(true),
    COMPLIANCE_RESTRICTION(true);

    private final boolean terminal;

    KycReviewReasonCode(boolean terminal) {
        this.terminal = terminal;
    }

    public boolean isTerminal() {
        return terminal;
    }
}
