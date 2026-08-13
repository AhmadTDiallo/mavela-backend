package com.mavela.backend.customer;

public enum KycStatus {
    NOT_STARTED,
    IN_PROGRESS,
    /** Customer-facing label: Pending verification. */
    SUBMITTED,
    UNDER_REVIEW,
    /** Customer-facing label: Verified. */
    APPROVED,
    REJECTED,
    /** Customer-facing label: Needs more information. */
    RESUBMISSION_REQUIRED
}
