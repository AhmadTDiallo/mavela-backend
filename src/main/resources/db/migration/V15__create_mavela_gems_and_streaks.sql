/*
 * Mavela Gems is an immutable, non-monetary rewards ledger. Amounts are
 * signed integers: awards are positive and any future redemption, reversal,
 * or expiry entry will be negative. No customer financial balance is stored
 * in this table.
 */
CREATE TABLE gems_ledger_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID NOT NULL,
    entry_type VARCHAR(32) NOT NULL,
    amount INTEGER NOT NULL,
    status VARCHAR(16) NOT NULL,
    business_date DATE NOT NULL,
    reference VARCHAR(200) NOT NULL,
    idempotency_key VARCHAR(160),
    actor_type VARCHAR(24) NOT NULL,
    actor_reference VARCHAR(120),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_gems_ledger_entries_customer
        FOREIGN KEY (customer_id)
        REFERENCES customers(id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_gems_ledger_entries_entry_type
        CHECK (entry_type IN (
            'DAILY_CHECK_IN',
            'STREAK_MILESTONE',
            'MANUAL_ADJUSTMENT',
            'REDEMPTION',
            'REVERSAL',
            'EXPIRATION'
        )),

    CONSTRAINT chk_gems_ledger_entries_status
        CHECK (status IN ('AVAILABLE', 'PENDING', 'REVERSED')),

    CONSTRAINT chk_gems_ledger_entries_amount
        CHECK (amount <> 0),

    CONSTRAINT chk_gems_ledger_entries_reference
        CHECK (btrim(reference) <> ''),

    CONSTRAINT chk_gems_ledger_entries_idempotency_key
        CHECK (idempotency_key IS NULL OR btrim(idempotency_key) <> ''),

    CONSTRAINT chk_gems_ledger_entries_actor_type
        CHECK (actor_type IN ('SYSTEM', 'CUSTOMER', 'STAFF')),

    CONSTRAINT chk_gems_ledger_entries_actor_reference
        CHECK (actor_reference IS NULL OR btrim(actor_reference) <> '')
);

/* Only one daily check-in award may exist for a Kinshasa business date. */
CREATE UNIQUE INDEX uq_gems_ledger_daily_check_in_customer_date
    ON gems_ledger_entries (customer_id, business_date)
    WHERE entry_type = 'DAILY_CHECK_IN';

/* Future command handlers may safely use an opaque per-customer key. */
CREATE UNIQUE INDEX uq_gems_ledger_customer_idempotency_key
    ON gems_ledger_entries (customer_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX idx_gems_ledger_entries_customer_created_at
    ON gems_ledger_entries (customer_id, created_at DESC);

CREATE INDEX idx_gems_ledger_entries_customer_business_date
    ON gems_ledger_entries (customer_id, business_date DESC);

CREATE INDEX idx_gems_ledger_entries_customer_status
    ON gems_ledger_entries (customer_id, status);

CREATE TABLE customer_reward_streaks (
    customer_id UUID PRIMARY KEY,
    current_streak_days INTEGER NOT NULL DEFAULT 0,
    last_qualified_business_date DATE,
    last_check_in_business_date DATE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_customer_reward_streaks_customer
        FOREIGN KEY (customer_id)
        REFERENCES customers(id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_customer_reward_streaks_current_streak_days
        CHECK (current_streak_days >= 0),

    CONSTRAINT chk_customer_reward_streaks_dates
        CHECK (
            last_check_in_business_date IS NULL
            OR last_qualified_business_date IS NULL
            OR last_check_in_business_date >= last_qualified_business_date
        )
);

CREATE INDEX idx_customer_reward_streaks_last_qualified_business_date
    ON customer_reward_streaks (last_qualified_business_date DESC);
