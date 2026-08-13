/*
 * Staff-only KYC review foundation. Customer evidence remains in private
 * object storage; this migration stores only staff allowlist and review audit
 * metadata.
 */
CREATE TABLE staff_users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    external_subject VARCHAR(255) NOT NULL,
    email VARCHAR(254) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    status VARCHAR(24) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_staff_users_external_subject
        UNIQUE (external_subject),

    CONSTRAINT chk_staff_users_external_subject
        CHECK (btrim(external_subject) <> ''),

    CONSTRAINT chk_staff_users_email
        CHECK (btrim(email) <> ''),

    CONSTRAINT chk_staff_users_display_name
        CHECK (btrim(display_name) <> ''),

    CONSTRAINT chk_staff_users_status
        CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE INDEX idx_staff_users_status
    ON staff_users (status);

/*
 * review_started_at and version were introduced by V11/V12 respectively.
 * Keep those existing columns and add only the assignment and completion
 * metadata required by the staff review workflow.
 */
ALTER TABLE kyc_applications
    ADD COLUMN assigned_reviewer_id UUID,
    ADD COLUMN reviewed_at TIMESTAMPTZ;

ALTER TABLE kyc_applications
    ADD CONSTRAINT fk_kyc_applications_assigned_reviewer
        FOREIGN KEY (assigned_reviewer_id)
        REFERENCES staff_users(id)
        ON DELETE RESTRICT;

CREATE INDEX idx_kyc_applications_status_submitted_at
    ON kyc_applications (status, submitted_at ASC);

CREATE INDEX idx_kyc_applications_assigned_reviewer
    ON kyc_applications (assigned_reviewer_id)
    WHERE assigned_reviewer_id IS NOT NULL;

/*
 * Review events are append-only through application behavior. They hold no
 * object-storage key, URL, image bytes, credentials, or authentication token.
 */
CREATE TABLE kyc_review_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    kyc_application_id UUID NOT NULL,
    reviewer_id UUID NOT NULL,
    action VARCHAR(40) NOT NULL,
    previous_status VARCHAR(24) NOT NULL,
    new_status VARCHAR(24) NOT NULL,
    reason_code VARCHAR(64),
    customer_message VARCHAR(500),
    internal_notes VARCHAR(2000),
    correlation_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_kyc_review_events_application
        FOREIGN KEY (kyc_application_id)
        REFERENCES kyc_applications(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_kyc_review_events_reviewer
        FOREIGN KEY (reviewer_id)
        REFERENCES staff_users(id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_kyc_review_events_action
        CHECK (action IN (
            'APPLICATION_VIEWED',
            'EVIDENCE_VIEWED',
            'CLAIMED',
            'RELEASED',
            'REASSIGNED',
            'APPROVED',
            'RESUBMISSION_REQUESTED',
            'REJECTED'
        )),

    CONSTRAINT chk_kyc_review_events_previous_status
        CHECK (previous_status IN (
            'IN_PROGRESS',
            'SUBMITTED',
            'UNDER_REVIEW',
            'APPROVED',
            'REJECTED',
            'RESUBMISSION_REQUIRED'
        )),

    CONSTRAINT chk_kyc_review_events_new_status
        CHECK (new_status IN (
            'IN_PROGRESS',
            'SUBMITTED',
            'UNDER_REVIEW',
            'APPROVED',
            'REJECTED',
            'RESUBMISSION_REQUIRED'
        )),

    CONSTRAINT chk_kyc_review_events_reason_code
        CHECK (
            reason_code IS NULL
            OR reason_code IN (
                'DOCUMENT_UNREADABLE',
                'DOCUMENT_EXPIRED',
                'SELFIE_UNCLEAR',
                'PROFILE_INFORMATION_MISMATCH',
                'MISSING_REQUIRED_INFORMATION',
                'IDENTITY_MISMATCH',
                'DOCUMENT_FRAUD_SUSPECTED',
                'DUPLICATE_IDENTITY',
                'COMPLIANCE_RESTRICTION'
            )
        ),

    CONSTRAINT chk_kyc_review_events_reason_for_customer_action
        CHECK (
            action NOT IN ('RESUBMISSION_REQUESTED', 'REJECTED')
            OR (
                reason_code IS NOT NULL
                AND btrim(reason_code) <> ''
                AND customer_message IS NOT NULL
                AND btrim(customer_message) <> ''
            )
        )
);

CREATE INDEX idx_kyc_review_events_application_created_at
    ON kyc_review_events (kyc_application_id, created_at ASC);

CREATE INDEX idx_kyc_review_events_reviewer_created_at
    ON kyc_review_events (reviewer_id, created_at ASC);

/*
 * A normalized evidence reference keeps review-event audit data free of
 * storage locations while preserving which evidence motivated a decision.
 */
CREATE TABLE kyc_review_event_evidence (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    review_event_id UUID NOT NULL,
    evidence_id UUID NOT NULL,

    CONSTRAINT fk_kyc_review_event_evidence_event
        FOREIGN KEY (review_event_id)
        REFERENCES kyc_review_events(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_kyc_review_event_evidence_document
        FOREIGN KEY (evidence_id)
        REFERENCES kyc_documents(id)
        ON DELETE RESTRICT,

    CONSTRAINT uq_kyc_review_event_evidence
        UNIQUE (review_event_id, evidence_id)
);

CREATE INDEX idx_kyc_review_event_evidence_event
    ON kyc_review_event_evidence (review_event_id);

CREATE INDEX idx_kyc_review_event_evidence_document
    ON kyc_review_event_evidence (evidence_id);

/*
 * A resubmission can concern a missing required item rather than an existing
 * uploaded evidence item. Keep that structured audit information separate
 * from staff-only notes and customer-facing prose.
 */
CREATE TABLE kyc_review_event_missing_requirements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    review_event_id UUID NOT NULL,
    requirement VARCHAR(40) NOT NULL,

    CONSTRAINT fk_kyc_review_event_missing_requirements_event
        FOREIGN KEY (review_event_id)
        REFERENCES kyc_review_events(id)
        ON DELETE RESTRICT,

    CONSTRAINT uq_kyc_review_event_missing_requirements
        UNIQUE (review_event_id, requirement),

    CONSTRAINT chk_kyc_review_event_missing_requirements_value
        CHECK (requirement IN (
            'PROFILE_INFORMATION',
            'DOCUMENT_FRONT',
            'DOCUMENT_BACK',
            'DOCUMENT_PHOTO_PAGE',
            'SELFIE'
        ))
);

CREATE INDEX idx_kyc_review_event_missing_requirements_event
    ON kyc_review_event_missing_requirements (review_event_id);
