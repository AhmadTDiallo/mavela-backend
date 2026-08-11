CREATE TABLE phone_otp_challenges (
    id UUID PRIMARY KEY,

    customer_id UUID NOT NULL,

    code_hash VARCHAR(64) NOT NULL,

    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,

    attempt_count INTEGER NOT NULL DEFAULT 0,

    max_attempts INTEGER NOT NULL DEFAULT 5,

    consumed_at TIMESTAMP WITH TIME ZONE,

    invalidated_at TIMESTAMP WITH TIME ZONE,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_phone_otp_customer
        FOREIGN KEY (customer_id)
        REFERENCES customers(id)
        ON DELETE CASCADE,

    CONSTRAINT chk_phone_otp_attempt_count
        CHECK (
            attempt_count >= 0
            AND attempt_count <= max_attempts
        ),

    CONSTRAINT chk_phone_otp_max_attempts
        CHECK (max_attempts > 0)
);

CREATE INDEX idx_phone_otp_customer_created
    ON phone_otp_challenges (
        customer_id,
        created_at DESC
    );

CREATE UNIQUE INDEX uq_phone_otp_open_challenge
    ON phone_otp_challenges (customer_id)
    WHERE consumed_at IS NULL
      AND invalidated_at IS NULL;