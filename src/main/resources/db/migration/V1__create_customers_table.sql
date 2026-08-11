CREATE TABLE customers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    phone_number VARCHAR(16) NOT NULL,
    email VARCHAR(254),
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,

    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    kyc_status VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED',

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_customers_phone_e164
        CHECK (phone_number ~ '^\+[1-9][0-9]{7,14}$'),

    CONSTRAINT chk_customers_status
        CHECK (status IN ('PENDING', 'ACTIVE', 'SUSPENDED', 'CLOSED')),

    CONSTRAINT chk_customers_kyc_status
        CHECK (kyc_status IN (
            'NOT_STARTED',
            'PENDING',
            'VERIFIED',
            'REJECTED'
        ))
);

CREATE UNIQUE INDEX uq_customers_phone_number
    ON customers (phone_number);

CREATE UNIQUE INDEX uq_customers_email_lower
    ON customers (LOWER(email))
    WHERE email IS NOT NULL;