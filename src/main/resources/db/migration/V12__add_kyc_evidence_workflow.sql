/*
 * V12 extends the V11 customer KYC draft without changing existing KYC
 * records. Existing applications remain readable and begin at the first
 * resumable confirmation step.
 */
/*
 * Retain the V2 legacy fr-CD value while permitting the language codes used
 * by the authenticated profile API. Existing customer rows are not rewritten.
 */
ALTER TABLE customers
    DROP CONSTRAINT IF EXISTS chk_customers_preferred_locale;

ALTER TABLE customers
    ADD CONSTRAINT chk_customers_preferred_locale
    CHECK (preferred_locale IN ('fr', 'en', 'ln', 'sw', 'fr-CD'));

ALTER TABLE kyc_applications
    ADD COLUMN current_step VARCHAR(40) NOT NULL DEFAULT 'CONFIRM_INFORMATION',
    ADD COLUMN document_type VARCHAR(24),
    ADD COLUMN started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN last_saved_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN profile_first_name VARCHAR(100),
    ADD COLUMN profile_last_name VARCHAR(100),
    ADD COLUMN profile_preferred_locale VARCHAR(10),
    ADD COLUMN profile_date_of_birth DATE,
    ADD COLUMN profile_nationality VARCHAR(2),
    ADD COLUMN profile_gender VARCHAR(24),
    ADD COLUMN profile_address_line VARCHAR(200),
    ADD COLUMN profile_city VARCHAR(100),
    ADD COLUMN profile_province VARCHAR(100);

ALTER TABLE kyc_applications
    ADD CONSTRAINT chk_kyc_applications_current_step
    CHECK (current_step IN (
        'CONFIRM_INFORMATION',
        'COMPLETE_INFORMATION',
        'SELECT_DOCUMENT',
        'DOCUMENT_FRONT',
        'DOCUMENT_BACK',
        'SELFIE',
        'REVIEW'
    )),
    ADD CONSTRAINT chk_kyc_applications_document_type
    CHECK (document_type IS NULL OR document_type IN (
        'NATIONAL_ID',
        'PASSPORT',
        'DRIVER_LICENSE'
    )),
    ADD CONSTRAINT chk_kyc_applications_snapshot_nationality
    CHECK (
        profile_nationality IS NULL
        OR profile_nationality ~ '^[A-Z]{2}$'
    ),
    ADD CONSTRAINT chk_kyc_applications_snapshot_gender
    CHECK (profile_gender IS NULL OR profile_gender IN (
        'MALE',
        'FEMALE',
        'OTHER',
        'PREFER_NOT_TO_SAY'
    ));

CREATE TABLE kyc_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    kyc_application_id UUID NOT NULL,
    evidence_type VARCHAR(24) NOT NULL,
    document_type VARCHAR(24),
    document_side VARCHAR(24) NOT NULL,
    capture_method VARCHAR(24) NOT NULL,
    storage_key VARCHAR(512) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    sha256_checksum CHAR(64) NOT NULL,
    upload_status VARCHAR(24) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    uploaded_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,

    CONSTRAINT fk_kyc_documents_application
        FOREIGN KEY (kyc_application_id)
        REFERENCES kyc_applications(id)
        ON DELETE RESTRICT,

    CONSTRAINT uq_kyc_documents_storage_key
        UNIQUE (storage_key),

    CONSTRAINT chk_kyc_documents_evidence_type
        CHECK (evidence_type IN ('DOCUMENT', 'SELFIE')),

    CONSTRAINT chk_kyc_documents_document_type
        CHECK (document_type IS NULL OR document_type IN (
            'NATIONAL_ID',
            'PASSPORT',
            'DRIVER_LICENSE'
        )),

    CONSTRAINT chk_kyc_documents_document_side
        CHECK (document_side IN (
            'FRONT',
            'BACK',
            'PHOTO_PAGE',
            'NOT_APPLICABLE'
        )),

    CONSTRAINT chk_kyc_documents_capture_method
        CHECK (capture_method IN ('CAMERA_CAPTURE', 'GALLERY_UPLOAD')),

    CONSTRAINT chk_kyc_documents_upload_status
        CHECK (upload_status IN ('REQUESTED', 'VALIDATED', 'FAILED', 'REMOVED')),

    CONSTRAINT chk_kyc_documents_file_size
        CHECK (file_size > 0 AND file_size <= 10485760),

    CONSTRAINT chk_kyc_documents_sha256
        CHECK (sha256_checksum ~ '^[a-f0-9]{64}$'),

    CONSTRAINT chk_kyc_documents_evidence_shape
        CHECK (
            (
                evidence_type = 'SELFIE'
                AND document_type IS NULL
                AND document_side = 'NOT_APPLICABLE'
                AND capture_method = 'CAMERA_CAPTURE'
            )
            OR
            (
                evidence_type = 'DOCUMENT'
                AND document_type IS NOT NULL
                AND document_side IN ('FRONT', 'BACK', 'PHOTO_PAGE')
            )
        )
);

CREATE INDEX idx_kyc_documents_application_created
    ON kyc_documents (kyc_application_id, created_at);

CREATE UNIQUE INDEX uq_kyc_documents_active_evidence_slot
    ON kyc_documents (kyc_application_id, evidence_type, document_side)
    WHERE deleted_at IS NULL AND upload_status <> 'REMOVED';
