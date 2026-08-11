CREATE TABLE auth_refresh_tokens (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    family_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    replaced_by_token_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_auth_refresh_tokens_customer
        FOREIGN KEY (customer_id)
        REFERENCES customers(id)
        ON DELETE CASCADE,

    CONSTRAINT uq_auth_refresh_tokens_token_hash
        UNIQUE (token_hash),

    CONSTRAINT chk_auth_refresh_token_expiry
        CHECK (expires_at > created_at)
);

CREATE INDEX idx_auth_refresh_tokens_customer
    ON auth_refresh_tokens(customer_id);

CREATE INDEX idx_auth_refresh_tokens_family
    ON auth_refresh_tokens(family_id);

CREATE INDEX idx_auth_refresh_tokens_expiry
    ON auth_refresh_tokens(expires_at);