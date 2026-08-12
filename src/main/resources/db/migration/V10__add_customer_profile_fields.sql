ALTER TABLE customers
    ADD COLUMN date_of_birth DATE,
    ADD COLUMN nationality VARCHAR(2),
    ADD COLUMN gender VARCHAR(24),
    ADD COLUMN address_line VARCHAR(200),
    ADD COLUMN city VARCHAR(100),
    ADD COLUMN province VARCHAR(100),
    ADD COLUMN profile_completed_at TIMESTAMPTZ;

ALTER TABLE customers
    ADD CONSTRAINT chk_customers_nationality_format
    CHECK (
        nationality IS NULL
        OR nationality ~ '^[A-Z]{2}$'
    ),
    ADD CONSTRAINT chk_customers_gender
    CHECK (
        gender IS NULL
        OR gender IN ('MALE', 'FEMALE', 'OTHER', 'PREFER_NOT_TO_SAY')
    ),
    ADD CONSTRAINT chk_customers_date_of_birth
    CHECK (
        date_of_birth IS NULL
        OR date_of_birth >= DATE '1900-01-01'
    );
