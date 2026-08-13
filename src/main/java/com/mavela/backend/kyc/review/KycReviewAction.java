package com.mavela.backend.kyc.review;

/**
 * Immutable staff actions recorded against a KYC application. Values map to
 * the V14 audit constraint and are intentionally not client supplied.
 */
public enum KycReviewAction {
    APPLICATION_VIEWED,
    EVIDENCE_VIEWED,
    CLAIMED,
    RELEASED,
    REASSIGNED,
    APPROVED,
    RESUBMISSION_REQUESTED,
    REJECTED
}
