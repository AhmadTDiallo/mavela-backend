CREATE TABLE customer_pin_credentials (
    customer_id UUID PRIMARY KEY,
    pin_hash VARCHAR(255) NOT NULL,
    failed_attempt_count INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMP WITH TIME ZONE,
    last_failed_attempt_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_customer_pin_credentials_customer
        FOREIGN KEY (customer_id)
        REFERENCES customers(id)
        ON DELETE CASCADE,

    CONSTRAINT chk_customer_pin_failed_attempt_count
        CHECK (failed_attempt_count >= 0)
);