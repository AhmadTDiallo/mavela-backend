package com.mavela.backend.kyc;

/**
 * A domain-neutral readiness failure shared by customer submission and staff
 * approval. Each caller maps it into its own public API error contract.
 */
public class KycApplicationReadinessException extends RuntimeException {

    public enum Reason {
        PROFILE_INCOMPLETE,
        SUBMISSION_INCOMPLETE
    }

    private final Reason reason;
    private final KycDraftStep step;

    public KycApplicationReadinessException(
            Reason reason,
            KycDraftStep step
    ) {
        this.reason = reason;
        this.step = step;
    }

    public Reason getReason() {
        return reason;
    }

    public KycDraftStep getStep() {
        return step;
    }
}
