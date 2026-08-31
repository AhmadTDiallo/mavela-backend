/*
 * Staff-only catalogue configuration. This migration does not introduce a
 * provider, purchase path, payment action, or any account/funding data.
 */
UPDATE service_catalogue_entries
SET availability = 'COMING_SOON'
WHERE availability = 'AVAILABLE';

ALTER TABLE service_catalogue_entries
    DROP CONSTRAINT chk_service_catalogue_entries_availability;

ALTER TABLE service_catalogue_entries
    ADD CONSTRAINT chk_service_catalogue_entries_availability
        CHECK (availability IN ('COMING_SOON', 'UNAVAILABLE'));

ALTER TABLE service_catalogue_entries
    DROP CONSTRAINT uq_service_catalogue_entries_display_order;

ALTER TABLE service_catalogue_entries
    ADD CONSTRAINT uq_service_catalogue_entries_display_order
        UNIQUE (display_order) DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE service_catalogue_entries
    ADD COLUMN customer_visible BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE service_catalogue_admin_audit_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    staff_user_id UUID NOT NULL,
    service_code VARCHAR(64) NOT NULL,
    action VARCHAR(32) NOT NULL,
    previous_availability VARCHAR(16) NOT NULL,
    new_availability VARCHAR(16) NOT NULL,
    previous_customer_visible BOOLEAN NOT NULL,
    new_customer_visible BOOLEAN NOT NULL,
    previous_display_order SMALLINT NOT NULL,
    new_display_order SMALLINT NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    internal_note VARCHAR(500) NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_service_catalogue_admin_audit_staff
        FOREIGN KEY (staff_user_id)
        REFERENCES staff_users(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_service_catalogue_admin_audit_service
        FOREIGN KEY (service_code)
        REFERENCES service_catalogue_entries(service_code)
        ON DELETE RESTRICT,

    CONSTRAINT chk_service_catalogue_admin_audit_previous_availability
        CHECK (previous_availability IN ('COMING_SOON', 'UNAVAILABLE')),

    CONSTRAINT chk_service_catalogue_admin_audit_new_availability
        CHECK (new_availability IN ('COMING_SOON', 'UNAVAILABLE')),

    CONSTRAINT chk_service_catalogue_admin_audit_action
        CHECK (action IN (
            'CONFIGURATION_UPDATED',
            'DISPLAY_ORDER_REBALANCED'
        )),

    CONSTRAINT chk_service_catalogue_admin_audit_display_order
        CHECK (previous_display_order > 0 AND new_display_order > 0),

    CONSTRAINT chk_service_catalogue_admin_audit_reason_code
        CHECK (reason_code IN (
            'CUSTOMER_COMMUNICATION',
            'OPERATIONAL_AVAILABILITY',
            'SERVICE_ROADMAP_UPDATE',
            'ORDERING_CORRECTION',
            'COMPLIANCE_REVIEW'
        )),

    CONSTRAINT chk_service_catalogue_admin_audit_internal_note
        CHECK (btrim(internal_note) <> ''),

    CONSTRAINT chk_service_catalogue_admin_audit_idempotency_key
        CHECK (btrim(idempotency_key) <> ''),

    CONSTRAINT chk_service_catalogue_admin_audit_request_fingerprint
        CHECK (request_fingerprint ~ '^[a-f0-9]{64}$')
);

CREATE UNIQUE INDEX uq_service_catalogue_admin_audit_staff_service_key
    ON service_catalogue_admin_audit_events (
        staff_user_id,
        service_code,
        action,
        idempotency_key
    );

CREATE INDEX idx_service_catalogue_admin_audit_service_created_at
    ON service_catalogue_admin_audit_events (service_code, created_at DESC);

CREATE INDEX idx_service_catalogue_admin_audit_staff_created_at
    ON service_catalogue_admin_audit_events (staff_user_id, created_at DESC);
