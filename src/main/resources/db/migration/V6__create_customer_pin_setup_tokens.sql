CREATE TABLE customer_pin_setup_tokens (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    invalidated_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_customer_pin_setup_tokens_customer
        FOREIGN KEY (customer_id)
        REFERENCES customers(id)
        ON DELETE CASCADE
);

CREATE UNIQUE INDEX uq_customer_pin_setup_tokens_open
    ON customer_pin_setup_tokens(customer_id)
    WHERE consumed_at IS NULL
      AND invalidated_at IS NULL;