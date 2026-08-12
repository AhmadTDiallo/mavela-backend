ALTER TABLE customers
    DROP CONSTRAINT IF EXISTS chk_customers_active_username;

ALTER TABLE customers
    DROP CONSTRAINT IF EXISTS chk_customers_kyc_status;

UPDATE customers
SET kyc_status = CASE kyc_status
    WHEN 'PENDING' THEN 'SUBMITTED'
    WHEN 'VERIFIED' THEN 'APPROVED'
END
WHERE kyc_status IN ('PENDING', 'VERIFIED');

ALTER TABLE customers
    ALTER COLUMN kyc_status TYPE VARCHAR(24);

ALTER TABLE customers
    ADD CONSTRAINT chk_customers_kyc_status
    CHECK (kyc_status IN (
        'NOT_STARTED',
        'IN_PROGRESS',
        'SUBMITTED',
        'UNDER_REVIEW',
        'APPROVED',
        'REJECTED',
        'RESUBMISSION_REQUIRED'
    ));

/*
 * Keep V9's compatibility behavior: legacy ACTIVE customers without a
 * username remain readable, while every new or updated ACTIVE row must have
 * a username. This must be absent while the legacy KYC rows are normalized.
 */
ALTER TABLE customers
    ADD CONSTRAINT chk_customers_active_username
    CHECK (
        status <> 'ACTIVE'
        OR username IS NOT NULL
    )
    NOT VALID;

CREATE TABLE kyc_applications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID NOT NULL,
    attempt_number INTEGER NOT NULL,
    status VARCHAR(24) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    submitted_at TIMESTAMPTZ,
    review_started_at TIMESTAMPTZ,
    decided_at TIMESTAMPTZ,
    rejection_reason VARCHAR(500),

    CONSTRAINT fk_kyc_applications_customer
        FOREIGN KEY (customer_id)
        REFERENCES customers(id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_kyc_applications_attempt_number
        CHECK (attempt_number > 0),

    CONSTRAINT uq_kyc_applications_customer_attempt
        UNIQUE (customer_id, attempt_number),

    CONSTRAINT chk_kyc_applications_status
        CHECK (status IN (
            'IN_PROGRESS',
            'SUBMITTED',
            'UNDER_REVIEW',
            'APPROVED',
            'REJECTED',
            'RESUBMISSION_REQUIRED'
        ))
);

CREATE INDEX idx_kyc_applications_customer_attempt_desc
    ON kyc_applications (customer_id, attempt_number DESC);

CREATE UNIQUE INDEX uq_kyc_applications_active_customer
    ON kyc_applications (customer_id)
    WHERE status IN ('IN_PROGRESS', 'SUBMITTED', 'UNDER_REVIEW');
