ALTER TABLE customers
    ADD COLUMN preferred_locale VARCHAR(10) NOT NULL DEFAULT 'fr-CD';

ALTER TABLE customers
    ADD CONSTRAINT chk_customers_preferred_locale
    CHECK (preferred_locale IN ('en', 'fr-CD'));