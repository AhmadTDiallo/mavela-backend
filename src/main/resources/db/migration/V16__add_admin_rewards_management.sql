/*
 * Staff-only Mavela Gems operations. Ledger rows remain append-only: this
 * migration adds an optional immutable reversal link and a separate audit
 * record for staff-only notes, reasons, and idempotency values.
 */
ALTER TABLE customers
    ADD COLUMN public_id UUID;

UPDATE customers
SET public_id = gen_random_uuid()
WHERE public_id IS NULL;

ALTER TABLE customers
    ALTER COLUMN public_id SET NOT NULL,
    ALTER COLUMN public_id SET DEFAULT gen_random_uuid(),
    ADD CONSTRAINT uq_customers_public_id UNIQUE (public_id);

ALTER TABLE gems_ledger_entries
    ADD COLUMN public_id UUID;

UPDATE gems_ledger_entries
SET public_id = gen_random_uuid()
WHERE public_id IS NULL;

ALTER TABLE gems_ledger_entries
    ALTER COLUMN public_id SET NOT NULL,
    ALTER COLUMN public_id SET DEFAULT gen_random_uuid(),
    ADD CONSTRAINT uq_gems_ledger_entries_public_id UNIQUE (public_id);

ALTER TABLE gems_ledger_entries
    ADD COLUMN reversal_of_entry_id UUID;

ALTER TABLE gems_ledger_entries
    ADD CONSTRAINT fk_gems_ledger_entries_reversal_of_entry
        FOREIGN KEY (reversal_of_entry_id)
        REFERENCES gems_ledger_entries(id)
        ON DELETE RESTRICT;

CREATE UNIQUE INDEX uq_gems_ledger_entries_one_reversal_per_original
    ON gems_ledger_entries (reversal_of_entry_id)
    WHERE reversal_of_entry_id IS NOT NULL;

CREATE INDEX idx_gems_ledger_entries_customer_type_created_at
    ON gems_ledger_entries (customer_id, entry_type, created_at DESC);

CREATE INDEX idx_gems_ledger_entries_customer_public_created_at
    ON gems_ledger_entries (customer_id, public_id, created_at DESC);

CREATE TABLE rewards_admin_audit_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    staff_user_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    resulting_ledger_entry_id UUID NOT NULL,
    original_ledger_entry_id UUID,
    action VARCHAR(32) NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    internal_note VARCHAR(500) NOT NULL,
    amount INTEGER NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_rewards_admin_audit_events_staff
        FOREIGN KEY (staff_user_id)
        REFERENCES staff_users(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_rewards_admin_audit_events_customer
        FOREIGN KEY (customer_id)
        REFERENCES customers(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_rewards_admin_audit_events_resulting_entry
        FOREIGN KEY (resulting_ledger_entry_id)
        REFERENCES gems_ledger_entries(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_rewards_admin_audit_events_original_entry
        FOREIGN KEY (original_ledger_entry_id)
        REFERENCES gems_ledger_entries(id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_rewards_admin_audit_events_action
        CHECK (action IN ('MANUAL_ADJUSTMENT', 'REVERSAL')),

    CONSTRAINT chk_rewards_admin_audit_events_reason_code
        CHECK (reason_code IN (
            'CUSTOMER_SERVICE_CORRECTION',
            'PROMOTIONAL_CREDIT',
            'INCIDENT_REMEDIATION',
            'FRAUD_CORRECTION',
            'DUPLICATE_REWARD',
            'OPERATIONAL_CORRECTION'
        )),

    CONSTRAINT chk_rewards_admin_audit_events_internal_note
        CHECK (btrim(internal_note) <> ''),

    CONSTRAINT chk_rewards_admin_audit_events_amount
        CHECK (amount <> 0),

    CONSTRAINT chk_rewards_admin_audit_events_idempotency_key
        CHECK (btrim(idempotency_key) <> ''),

    CONSTRAINT chk_rewards_admin_audit_events_original_entry
        CHECK (
            (action = 'MANUAL_ADJUSTMENT' AND original_ledger_entry_id IS NULL)
            OR (action = 'REVERSAL' AND original_ledger_entry_id IS NOT NULL)
        )
);

/* A request replay by the same staff actor cannot double-credit/debit. */
CREATE UNIQUE INDEX uq_rewards_admin_audit_staff_customer_action_key
    ON rewards_admin_audit_events (
        staff_user_id,
        customer_id,
        action,
        idempotency_key
    );

CREATE INDEX idx_rewards_admin_audit_events_customer_created_at
    ON rewards_admin_audit_events (customer_id, created_at DESC);

CREATE INDEX idx_rewards_admin_audit_events_staff_created_at
    ON rewards_admin_audit_events (staff_user_id, created_at DESC);

CREATE UNIQUE INDEX uq_rewards_admin_audit_one_reversal_per_original
    ON rewards_admin_audit_events (original_ledger_entry_id)
    WHERE action = 'REVERSAL';
