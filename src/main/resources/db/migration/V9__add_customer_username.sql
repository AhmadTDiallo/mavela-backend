ALTER TABLE customers
    ADD COLUMN username VARCHAR(20);

ALTER TABLE customers
    ADD CONSTRAINT chk_customers_username_format
    CHECK (
        username IS NULL
        OR username ~ '^[a-z0-9]{3,20}$'
    );

CREATE UNIQUE INDEX uq_customers_username_lower
    ON customers (LOWER(username))
    WHERE username IS NOT NULL;

/*
 * NOT VALID allows existing development customers without usernames
 * to remain usable. The constraint is still enforced for new or
 * updated customer rows.
 */
ALTER TABLE customers
    ADD CONSTRAINT chk_customers_active_username
    CHECK (
        status <> 'ACTIVE'
        OR username IS NOT NULL
    )
    NOT VALID;